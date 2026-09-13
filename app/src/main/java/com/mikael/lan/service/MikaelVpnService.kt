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

class MikaelVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopVpn(); return START_NOT_STICKY }
        if (tunnel == null) startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        tunnel = Builder().setSession("MikaelLAN")
            .addAddress("10.10.0.2", 24)
            .addRoute("10.10.0.0", 24)
            .setBlocking(false).establish()
        val fd = tunnel ?: return
        job = CoroutineScope(Dispatchers.IO).launch {
            val input = FileInputStream(fd.fileDescriptor); val output = FileOutputStream(fd.fileDescriptor)
            val buffer = ByteArray(32767)
            try { while (true) { val count = input.read(buffer); if (count <= 0) break /* transporte P2P entra aqui */ } }
            finally { input.close(); output.close() }
        }
    }

    private fun stopVpn() { job?.cancel(); job = null; tunnel?.close(); tunnel = null; stopSelf() }
    override fun onDestroy() { stopVpn(); super.onDestroy() }
    companion object { const val ACTION_STOP = "com.mikael.lan.STOP_VPN" }
}
