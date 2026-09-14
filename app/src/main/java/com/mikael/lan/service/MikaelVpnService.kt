package com.mikael.lan.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class MikaelVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null
    private var scope: CoroutineScope? = null
    private var socket: DatagramSocket? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopVpn(); return START_NOT_STICKY }
        if (tunnel == null) startVpn(intent)
        return START_STICKY
    }

    private fun startVpn(intent: Intent?) {
        val virtualIp = intent?.getStringExtra(EXTRA_VIRTUAL_IP) ?: "10.10.0.2"
        val relayHost = intent?.getStringExtra(EXTRA_RELAY_HOST) ?: return
        val relayPort = intent.getIntExtra(EXTRA_RELAY_PORT, 3478)
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return
        tunnel = Builder().setSession("MikaelLAN")
            .addAddress(virtualIp, 24).addRoute("10.10.0.0", 24).setBlocking(false).establish()
        val fd = tunnel ?: return
        val udp = DatagramSocket(); if (!protect(udp)) { udp.close(); stopVpn(); return }; socket = udp
        val destination = InetSocketAddress(relayHost, relayPort)
        val framePrefix = byteArrayOf('M'.code.toByte(), 'K'.code.toByte(), 'L'.code.toByte(), '1'.code.toByte())
        scope = CoroutineScope(Dispatchers.IO)
        scope?.launch {
            val input = FileInputStream(fd.fileDescriptor); val buffer = ByteArray(32767)
            try { while (true) { val count = input.read(buffer); if (count <= 0) break; val device = deviceId.toByteArray(); val frame = ByteArray(4 + 2 + token.toByteArray().size + 2 + device.size + count); var offset = 0; framePrefix.copyInto(frame, offset); offset += 4; frame[offset++] = (token.toByteArray().size shr 8).toByte(); frame[offset++] = token.toByteArray().size.toByte(); token.toByteArray().copyInto(frame, offset); offset += token.toByteArray().size; frame[offset++] = (device.size shr 8).toByte(); frame[offset++] = device.size.toByte(); device.copyInto(frame, offset); offset += device.size; buffer.copyInto(frame, offset, 0, count); udp.send(DatagramPacket(frame, frame.size, destination)) } } finally { input.close() }
        }
        scope?.launch {
            val output = FileOutputStream(fd.fileDescriptor); val buffer = ByteArray(65535)
            try { while (true) { val packet = DatagramPacket(buffer, buffer.size); udp.receive(packet); val bytes = packet.data; if (packet.length < 8 || bytes[0] != 'M'.code.toByte() || bytes[1] != 'K'.code.toByte() || bytes[2] != 'L'.code.toByte() || bytes[3] != '1'.code.toByte()) continue; var offset = 4; val tokenLen = ((bytes[offset++].toInt() and 255) shl 8) or (bytes[offset++].toInt() and 255); offset += tokenLen; val deviceLen = ((bytes[offset++].toInt() and 255) shl 8) or (bytes[offset++].toInt() and 255); offset += deviceLen; if (offset < packet.length) { output.write(bytes, offset, packet.length - offset); output.flush() } } } finally { output.close() }
        }
    }

    private fun stopVpn() { scope?.cancel(); scope = null; socket?.close(); socket = null; tunnel?.close(); tunnel = null; stopSelf() }
    override fun onDestroy() { stopVpn(); super.onDestroy() }
    companion object { const val ACTION_STOP = "com.mikael.lan.STOP_VPN"; const val EXTRA_VIRTUAL_IP = "virtual_ip"; const val EXTRA_RELAY_HOST = "relay_host"; const val EXTRA_RELAY_PORT = "relay_port"; const val EXTRA_TOKEN = "token"; const val EXTRA_DEVICE_ID = "device_id" }
}
