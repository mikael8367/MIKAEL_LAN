package com.mikael.lan.data

import kotlinx.serialization.Serializable

@Serializable
data class Peer(val id: String, val name: String, val virtualIp: String, val pingMs: Int? = null, val connected: Boolean = true, val transport: String = "P2P", val role: String = "Membro")
@Serializable
data class LanNetwork(val id: String, val name: String, val secretHint: String, val virtualIp: String, val peers: List<Peer> = emptyList(), val connected: Boolean = false)
data class Diagnostic(val label: String, val status: Status, val detail: String)
data class LanWorld(val name: String, val hostName: String, val virtualIp: String, val port: Int, val latencyMs: Int, val available: Boolean = true)
data class NetworkSettings(val autoAccept: Boolean = true, val allowWorldDiscovery: Boolean = true, val maxPlayers: Int = 8, val isPublic: Boolean = false)
data class JoinRequest(val id: String, val playerName: String, val deviceId: String)
enum class Status { OK, WARN, ERROR }
