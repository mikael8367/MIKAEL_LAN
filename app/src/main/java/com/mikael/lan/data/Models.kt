package com.mikael.lan.data

import kotlinx.serialization.Serializable

@Serializable
data class Peer(val id: String, val name: String, val virtualIp: String, val pingMs: Int? = null, val connected: Boolean = true, val transport: String = "P2P")
@Serializable
data class LanNetwork(val id: String, val name: String, val secretHint: String, val virtualIp: String, val peers: List<Peer> = emptyList(), val connected: Boolean = false)
data class Diagnostic(val label: String, val status: Status, val detail: String)
enum class Status { OK, WARN, ERROR }
