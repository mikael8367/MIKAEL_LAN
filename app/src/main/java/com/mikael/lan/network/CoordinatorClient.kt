package com.mikael.lan.network

import com.mikael.lan.data.LanNetwork
import com.mikael.lan.data.Peer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

@Serializable private data class CreateBody(val name: String, val password: String, val ownerName: String, val deviceId: String)
@Serializable private data class JoinBody(val name: String, val password: String, val deviceId: String)
@Serializable private data class RelayInfo(val host: String = "", val port: Int = 3478)
@Serializable private data class CoordinatorResponse(val networkId: String? = null, val id: String? = null, val name: String, val secretHint: String = "••••••••", val token: String = "", val virtualIp: String = "10.10.0.2", val peers: List<Peer> = emptyList(), val relay: RelayInfo? = null, val connected: Boolean = true)

class CoordinatorClient(private val baseUrl: String = "https://mikaellan-coordinator.example.com") {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val media = "application/json".toMediaType()
    val deviceId: String = UUID.randomUUID().toString()

    suspend fun createNetwork(name: String, password: String, ownerName: String = "Jogador"): Result<LanNetwork> = withContext(Dispatchers.IO) { post("/v1/networks", json.encodeToString(CreateBody.serializer(), CreateBody(name, password, ownerName, deviceId)), null).map { it.toNetwork() } }
    suspend fun joinNetwork(name: String, password: String): Result<LanNetwork> = withContext(Dispatchers.IO) { post("/v1/networks/${name.trim()}/join", json.encodeToString(JoinBody.serializer(), JoinBody(name, password, deviceId)), null).map { it.toNetwork() } }
    suspend fun fetchNetwork(networkId: String, token: String): Result<LanNetwork> = withContext(Dispatchers.IO) { get("/v1/networks/${networkId.trim()}/peers", token).map { it.toNetwork() } }
    private fun post(path: String, body: String, token: String?): Result<CoordinatorResponse> = runCatching { val builder = Request.Builder().url(baseUrl.trimEnd('/') + path).post(body.toRequestBody(media)); if (token != null) builder.header("Authorization", "Bearer $token"); http.newCall(builder.build()).execute().use { response -> if (!response.isSuccessful) error("Coordenador HTTP ${response.code}"); json.decodeFromString<CoordinatorResponse>(response.body?.string() ?: error("Resposta vazia")) } }
    private fun get(path: String, token: String): Result<CoordinatorResponse> = runCatching { val req = Request.Builder().url(baseUrl.trimEnd('/') + path).header("Authorization", "Bearer $token").build(); http.newCall(req).execute().use { response -> if (!response.isSuccessful) error("Coordenador HTTP ${response.code}"); json.decodeFromString<CoordinatorResponse>(response.body?.string() ?: error("Resposta vazia")) } }
    private fun CoordinatorResponse.toNetwork() = LanNetwork(networkId ?: id ?: name, name, secretHint, virtualIp, peers, connected, token, relay?.host ?: "", relay?.port ?: 3478, deviceId)
    suspend fun ping(host: String): Long = withContext(Dispatchers.IO) { val start = System.nanoTime(); val req = Request.Builder().url(host.trimEnd('/') + "/health").build(); http.newCall(req).execute().use { it.close() }; (System.nanoTime() - start) / 1_000_000 }
    fun localDemoNetwork(): LanNetwork = LanNetwork("DEMO-7K2P", "MinhaRede", "••••••••", "10.10.0.2", listOf(Peer(deviceId, "Este dispositivo", "10.10.0.2", 20, true, "RELAY"), Peer("demo-peer", "Player2", "10.10.0.3", 35, true, "RELAY")), true, "demo-token", "127.0.0.1", 3478, deviceId)
}
