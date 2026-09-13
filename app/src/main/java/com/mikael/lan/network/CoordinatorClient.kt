package com.mikael.lan.network

import com.mikael.lan.data.LanNetwork
import com.mikael.lan.data.Peer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

class CoordinatorClient(private val baseUrl: String = "https://mikaellan-coordinator.example.com") {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    val deviceId: String = UUID.randomUUID().toString()

    suspend fun fetchNetwork(networkId: String, token: String): Result<LanNetwork> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/v1/networks/${networkId.trim()}/peers")
                .header("Authorization", "Bearer $token").build()
            http.newCall(req).execute().use { response ->
                if (!response.isSuccessful) error("Coordenador respondeu HTTP ${response.code}")
                json.decodeFromString<LanNetwork>(response.body?.string() ?: error("Resposta vazia"))
            }
        }
    }

    suspend fun ping(host: String): Long = withContext(Dispatchers.IO) {
        val start = System.nanoTime(); val req = Request.Builder().url(host.trimEnd('/') + "/health").build()
        http.newCall(req).execute().use { it.close() }; (System.nanoTime() - start) / 1_000_000
    }

    fun localDemoNetwork(): LanNetwork = LanNetwork("DEMO-7K2P", "MinhaRede", "••••••••", "10.10.0.2", listOf(
        Peer(deviceId, "Este dispositivo", "10.10.0.2", 20, true, "P2P"),
        Peer("demo-peer", "Player2", "10.10.0.3", 35, true, "P2P")
    ), true)
}
