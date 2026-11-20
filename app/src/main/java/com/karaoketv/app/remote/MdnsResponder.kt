package com.karaoketv.app.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

class MdnsResponder(
    private val context: Context,
    private val hostLabel: String = "karaokenow",
    private val port: Int = 8888
) {
    private val tag = "MdnsResponder"
    private val fqdn = "$hostLabel.local"
    private val serviceInstanceFqdn = "$hostLabel._http._tcp.local"
    private val serviceTypeFqdn = "_http._tcp.local"
    private val metaQueryFqdn = "_services._dns-sd._udp.local"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var responderJob: Job? = null
    private var announceJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var socket: MulticastSocket? = null
    private var nsdManager: NsdManager? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val running = AtomicBoolean(false)
    @Volatile
    private var lastAnnouncedIp: String? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return

        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("karaokenow_mdns")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Log.w(tag, "MulticastLock acquire failed: ${it.message}") }

        registerNetworkCallback()
        startSocketAndNsd()
    }

    fun restartForNetworkChange() {
        if (!running.get()) return
        scope.launch {
            Log.i(tag, "Restarting mDNS responder after network change")
            stopSocketAndNsd()
            delay(400)
            if (running.get()) {
                startSocketAndNsd()
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        unregisterNetworkCallback()
        stopSocketAndNsd()
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        multicastLock = null
    }

    private fun startSocketAndNsd() {
        registerNsdService()

        responderJob?.cancel()
        announceJob?.cancel()

        responderJob = scope.launch {
            while (running.get() && isActive) {
                val ifacePair = findActiveIpv4Interface()
                if (ifacePair == null) {
                    Log.w(tag, "No active IPv4 interface yet; retrying in 2s")
                    delay(2_000)
                    continue
                }
                val (netIf, ipv4Addr) = ifacePair
                val ipText = ipv4Addr.hostAddress
                Log.i(tag, "Binding mDNS on ${netIf.name} ($ipText) for $fqdn:$port")

                var sock: MulticastSocket? = null
                try {
                    val group = InetAddress.getByName("224.0.0.251")
                    sock = MulticastSocket(null).apply {
                        reuseAddress = true
                        bind(InetSocketAddress(5353))
                        timeToLive = 255
                        networkInterface = netIf
                        joinGroup(InetSocketAddress(group, 5353), netIf)
                    }
                    socket = sock
                    lastAnnouncedIp = ipText

                    // Rapid initial announcements
                    launch {
                        repeat(3) { idx ->
                            if (!running.get() || sock.isClosed) return@launch
                            runCatching { sendHostAnnouncement(sock, group, ipv4Addr.address) }
                            delay(if (idx == 0) 500L else 1_000L)
                        }
                    }

                    // Periodic re-announcements (TTL = 120s, refresh every 45s)
                    announceJob?.cancel()
                    announceJob = launch {
                        while (running.get() && !sock.isClosed && isActive) {
                            delay(45_000)
                            val current = findActiveIpv4Interface()
                            if (current == null || current.second.hostAddress != lastAnnouncedIp) {
                                Log.i(tag, "IP changed ($lastAnnouncedIp -> ${current?.second?.hostAddress}), rebinding")
                                runCatching { sock.close() }
                                break
                            }
                            runCatching { sendHostAnnouncement(sock, group, current.second.address) }
                        }
                    }

                    val buf = ByteArray(1500)
                    while (running.get() && !sock.isClosed && isActive) {
                        val pkt = DatagramPacket(buf, buf.size)
                        sock.receive(pkt)
                        if (pkt.length > 12) {
                            handleIncomingPacket(sock, group, pkt, ipv4Addr.address)
                        }
                    }
                } catch (e: Exception) {
                    if (running.get()) {
                        Log.w(tag, "mDNS socket loop error: ${e.message}")
                    }
                } finally {
                    announceJob?.cancel()
                    runCatching { sock?.close() }
                    if (socket === sock) socket = null
                }

                if (running.get()) {
                    delay(1_500)
                }
            }
        }
    }

    private fun stopSocketAndNsd() {
        announceJob?.cancel()
        responderJob?.cancel()
        runCatching { socket?.close() }
        socket = null
        runCatching {
            nsdListener?.let { nsdManager?.unregisterService(it) }
        }
        nsdListener = null
    }

    private fun registerNetworkCallback() {
        runCatching {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val activeIp = findActiveIpv4Interface()?.second?.hostAddress
                    if (activeIp != null && activeIp != lastAnnouncedIp) {
                        restartForNetworkChange()
                    }
                }

                override fun onLost(network: Network) {
                    lastAnnouncedIp = null
                }
            }
            networkCallback = cb
            cm.registerNetworkCallback(request, cb)
        }.onFailure { Log.w(tag, "NetworkCallback register failed: ${it.message}") }
    }

    private fun unregisterNetworkCallback() {
        runCatching {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
        }
        networkCallback = null
    }

    private fun registerNsdService() {
        runCatching {
            val mgr = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = mgr
            val info = NsdServiceInfo().apply {
                serviceName = hostLabel
                serviceType = "_http._tcp."
                setPort(port)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Log.i(tag, "NSD service registered: ${serviceInfo.serviceName}")
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(tag, "NSD registration failed: $errorCode")
                }
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            nsdListener = listener
            mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun handleIncomingPacket(
        sock: MulticastSocket,
        group: InetAddress,
        pkt: DatagramPacket,
        ipv4: ByteArray
    ) {
        val data = pkt.data
        val length = pkt.length
        val txId = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val flags = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        if ((flags and 0x8000) != 0) return // Ignore responses

        val qdCount = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        if (qdCount <= 0) return

        var offset = 12
        var wantHostA = false
        var wantHostAAAA = false
        var wantServicePtr = false
        var wantMetaPtr = false
        var wantServiceDetails = false
        var sendUnicast = pkt.port != 5353

        for (i in 0 until qdCount) {
            val (name, nextOffset) = readDnsName(data, offset, length) ?: return
            if (nextOffset + 4 > length) return
            val qtype = ((data[nextOffset].toInt() and 0xFF) shl 8) or (data[nextOffset + 1].toInt() and 0xFF)
            val qclassRaw = ((data[nextOffset + 2].toInt() and 0xFF) shl 8) or (data[nextOffset + 3].toInt() and 0xFF)
            if ((qclassRaw and 0x8000) != 0) sendUnicast = true
            offset = nextOffset + 4

            when {
                name.equals(fqdn, ignoreCase = true) -> {
                    when (qtype) {
                        1, 255 -> wantHostA = true
                        28 -> wantHostAAAA = true
                    }
                }
                name.equals(serviceTypeFqdn, ignoreCase = true) && (qtype == 12 || qtype == 255) -> {
                    wantServicePtr = true
                }
                name.equals(metaQueryFqdn, ignoreCase = true) && (qtype == 12 || qtype == 255) -> {
                    wantMetaPtr = true
                }
                name.equals(serviceInstanceFqdn, ignoreCase = true) -> {
                    wantServiceDetails = true
                }
            }
        }

        val respId = if (pkt.port != 5353) txId else 0
        val packetsToSend = mutableListOf<ByteArray>()
        if (wantHostA) {
            packetsToSend += buildARecordResponse(respId, fqdn, ipv4)
        }
        if (wantHostAAAA) {
            // Negative NSEC response for AAAA + include A record in Additional so macOS resolves fast
            packetsToSend += buildNsecForAOnlyResponse(respId, fqdn, ipv4)
        }
        if (wantMetaPtr) {
            packetsToSend += buildPtrResponse(respId, metaQueryFqdn, serviceTypeFqdn)
        }
        if (wantServicePtr || wantServiceDetails) {
            packetsToSend += buildServiceResponse(respId, ipv4)
        }

        for (bytes in packetsToSend) {
            if (sendUnicast && pkt.address != null) {
                runCatching { sock.send(DatagramPacket(bytes, bytes.size, pkt.address, pkt.port)) }
            }
            runCatching { sock.send(DatagramPacket(bytes, bytes.size, group, 5353)) }
        }
    }

    private fun sendHostAnnouncement(sock: MulticastSocket, group: InetAddress, ipv4: ByteArray) {
        val aPkt = buildARecordResponse(0, fqdn, ipv4)
        sock.send(DatagramPacket(aPkt, aPkt.size, group, 5353))
        val svcPkt = buildServiceResponse(0, ipv4)
        sock.send(DatagramPacket(svcPkt, svcPkt.size, group, 5353))
    }

    private fun readDnsName(data: ByteArray, start: Int, limit: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var idx = start
        var jumped = false
        var returnOffset = -1
        var hops = 0
        while (idx < limit && hops < 32) {
            hops++
            val len = data[idx].toInt() and 0xFF
            if (len == 0) {
                if (!jumped) returnOffset = idx + 1
                return sb.toString() to returnOffset
            }
            if ((len and 0xC0) == 0xC0) {
                if (idx + 1 >= limit) return null
                val ptr = ((len and 0x3F) shl 8) or (data[idx + 1].toInt() and 0xFF)
                if (!jumped) returnOffset = idx + 2
                jumped = true
                idx = ptr
                continue
            }
            idx++
            if (idx + len > limit) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(data, idx, len, Charsets.UTF_8))
            idx += len
        }
        return null
    }

    private fun writeDnsName(dos: DataOutputStream, name: String) {
        for (label in name.split(".")) {
            if (label.isEmpty()) continue
            val bytes = label.toByteArray(Charsets.UTF_8)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)
    }

    private fun buildARecordResponse(txId: Int, targetFqdn: String, ipv4: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(txId)
        dos.writeShort(0x8400) // Authoritative response
        dos.writeShort(0) // Questions
        dos.writeShort(1) // Answer RRs
        dos.writeShort(0) // Authority RRs
        dos.writeShort(0) // Additional RRs

        writeDnsName(dos, targetFqdn)
        dos.writeShort(1) // Type A
        dos.writeShort(0x8001) // Class IN + cache-flush
        dos.writeInt(120) // TTL
        dos.writeShort(4)
        dos.write(ipv4)
        dos.flush()
        return baos.toByteArray()
    }

    private fun buildNsecForAOnlyResponse(txId: Int, targetFqdn: String, ipv4: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(txId)
        dos.writeShort(0x8400)
        dos.writeShort(0)
        dos.writeShort(1) // Answer: NSEC
        dos.writeShort(0)
        dos.writeShort(1) // Additional: A

        // NSEC indicating only Type A (bit 1 -> 0x40 in window 0) exists
        writeDnsName(dos, targetFqdn)
        dos.writeShort(47) // Type NSEC
        dos.writeShort(0x8001)
        dos.writeInt(120)
        val nsecBody = ByteArrayOutputStream().apply {
            val ndos = DataOutputStream(this)
            writeDnsName(ndos, targetFqdn)
            ndos.writeByte(0) // Block 0
            ndos.writeByte(1) // Bitmap length 1
            ndos.writeByte(0x40) // Type 1 (A) present
            ndos.flush()
        }.toByteArray()
        dos.writeShort(nsecBody.size)
        dos.write(nsecBody)

        // Additional A record
        writeDnsName(dos, targetFqdn)
        dos.writeShort(1)
        dos.writeShort(0x8001)
        dos.writeInt(120)
        dos.writeShort(4)
        dos.write(ipv4)

        dos.flush()
        return baos.toByteArray()
    }

    private fun buildPtrResponse(txId: Int, ptrName: String, targetName: String): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(txId)
        dos.writeShort(0x8400)
        dos.writeShort(0)
        dos.writeShort(1)
        dos.writeShort(0)
        dos.writeShort(0)

        writeDnsName(dos, ptrName)
        dos.writeShort(12) // PTR
        dos.writeShort(1) // IN
        dos.writeInt(120)
        val targetBytes = ByteArrayOutputStream().apply {
            val td = DataOutputStream(this)
            writeDnsName(td, targetName)
            td.flush()
        }.toByteArray()
        dos.writeShort(targetBytes.size)
        dos.write(targetBytes)
        dos.flush()
        return baos.toByteArray()
    }

    private fun buildServiceResponse(txId: Int, ipv4: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(txId)
        dos.writeShort(0x8400)
        dos.writeShort(0)
        dos.writeShort(4) // PTR + SRV + TXT + A
        dos.writeShort(0)
        dos.writeShort(0)

        // 1. PTR _http._tcp.local -> karaokenow._http._tcp.local
        writeDnsName(dos, serviceTypeFqdn)
        dos.writeShort(12)
        dos.writeShort(1)
        dos.writeInt(120)
        val ptrBody = ByteArrayOutputStream().apply {
            val d = DataOutputStream(this)
            writeDnsName(d, serviceInstanceFqdn)
            d.flush()
        }.toByteArray()
        dos.writeShort(ptrBody.size)
        dos.write(ptrBody)

        // 2. SRV karaokenow._http._tcp.local -> karaokenow.local:8888
        writeDnsName(dos, serviceInstanceFqdn)
        dos.writeShort(33)
        dos.writeShort(0x8001)
        dos.writeInt(120)
        val srvBody = ByteArrayOutputStream().apply {
            val d = DataOutputStream(this)
            d.writeShort(0) // Priority
            d.writeShort(0) // Weight
            d.writeShort(port)
            writeDnsName(d, fqdn)
            d.flush()
        }.toByteArray()
        dos.writeShort(srvBody.size)
        dos.write(srvBody)

        // 3. TXT karaokenow._http._tcp.local
        writeDnsName(dos, serviceInstanceFqdn)
        dos.writeShort(16)
        dos.writeShort(0x8001)
        dos.writeInt(120)
        val txt = "path=/".toByteArray(Charsets.UTF_8)
        dos.writeShort(txt.size + 1)
        dos.writeByte(txt.size)
        dos.write(txt)

        // 4. A karaokenow.local
        writeDnsName(dos, fqdn)
        dos.writeShort(1)
        dos.writeShort(0x8001)
        dos.writeInt(120)
        dos.writeShort(4)
        dos.write(ipv4)

        dos.flush()
        return baos.toByteArray()
    }

    private fun findActiveIpv4Interface(): Pair<NetworkInterface, Inet4Address>? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()?.toList()
            ?: return null
        val preferredNames = listOf("wlan0", "eth0", "en0")
        val sorted = interfaces.sortedBy { intf ->
            val idx = preferredNames.indexOf(intf.name)
            if (idx >= 0) idx else 100
        }
        for (intf in sorted) {
            if (!intf.isUp || intf.isLoopback || !intf.supportsMulticast()) continue
            for (addr in intf.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return intf to addr
                }
            }
        }
        return null
    }
}
