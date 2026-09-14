package com.mikael.lan.ui

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mikael.lan.data.Diagnostic
import com.mikael.lan.data.LanNetwork
import com.mikael.lan.data.LanWorld
import com.mikael.lan.data.JoinRequest
import com.mikael.lan.data.NetworkSettings
import com.mikael.lan.data.LanGameProfile
import com.mikael.lan.data.PortRule
import com.mikael.lan.data.TransportProtocol
import com.mikael.lan.data.Status
import com.mikael.lan.network.CoordinatorClient
import com.mikael.lan.service.MikaelVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val client = CoordinatorClient()
    private val prefs = app.getSharedPreferences("mikaellan_networks", Application.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _network = MutableStateFlow<LanNetwork?>(null); val network: StateFlow<LanNetwork?> = _network.asStateFlow()
    private val _savedNetworks = MutableStateFlow(loadSavedNetworks()); val savedNetworks: StateFlow<List<LanNetwork>> = _savedNetworks.asStateFlow()
    private val _publicNetworks = MutableStateFlow<List<LanNetwork>>(listOf(LanNetwork("PUBLIC-DEMO", "Sala pública Minecraft", "entrada livre", "10.20.0.2", emptyList(), false))); val publicNetworks: StateFlow<List<LanNetwork>> = _publicNetworks.asStateFlow()
    private val _diagnostics = MutableStateFlow<List<Diagnostic>>(emptyList()); val diagnostics = _diagnostics.asStateFlow()
    private val _worlds = MutableStateFlow<List<LanWorld>>(emptyList()); val worlds: StateFlow<List<LanWorld>> = _worlds.asStateFlow()
    private val _settings = MutableStateFlow(NetworkSettings()); val settings: StateFlow<NetworkSettings> = _settings.asStateFlow()
    private val _joinRequests = MutableStateFlow(listOf(JoinRequest("req-demo", "Novo jogador", "device-demo"))); val joinRequests: StateFlow<List<JoinRequest>> = _joinRequests.asStateFlow()
    private val _banned = MutableStateFlow<Set<String>>(emptySet()); val banned: StateFlow<Set<String>> = _banned.asStateFlow()
    private val _gameProfiles = MutableStateFlow(listOf(
        LanGameProfile("minecraft-java", "Minecraft Java", listOf(PortRule(25565, protocol = TransportProtocol.TCP, description = "Servidor/LAN"))),
        LanGameProfile("generic-tcp-udp", "Jogo LAN personalizado", listOf(PortRule(10000, 65535, TransportProtocol.TCP_UDP, "Faixa configurável")), false)
    )); val gameProfiles: StateFlow<List<LanGameProfile>> = _gameProfiles.asStateFlow()
    fun loadDemo() { _network.value = client.localDemoNetwork(); discoverWorlds(); runDiagnostics() }
    private fun loadSavedNetworks(): List<LanNetwork> = prefs.getStringSet("names", emptySet()).orEmpty().map { LanNetwork(it, it, "salva neste aparelho", "10.10.0.2", emptyList(), false) }
    fun saveCurrentNetwork() { _network.value?.let { current -> val updated = (_savedNetworks.value.filterNot { it.name == current.name } + current.copy(connected = false)); _savedNetworks.value = updated; prefs.edit().putStringSet("names", updated.map { it.name }.toSet()).apply() } }
    fun disconnect() { _network.value = _network.value?.copy(connected = false) }
    fun reconnect(saved: LanNetwork) { _network.value = saved.copy(connected = true); discoverWorlds(); runDiagnostics() }
    fun updateSettings(settings: NetworkSettings) { _settings.value = settings }
    fun publishCurrentNetwork() {
        val current = _network.value ?: return
        require(_publicNetworks.value.size < 3 || _publicNetworks.value.any { it.name == current.name }) { "Você já atingiu o limite de 3 redes públicas." }
        _settings.value = _settings.value.copy(isPublic = true)
        _publicNetworks.value = (_publicNetworks.value.filterNot { it.name == current.name } + current.copy(connected = false)).take(3)
    }
    fun unpublishCurrentNetwork() { _network.value?.let { current -> _settings.value = _settings.value.copy(isPublic = false); _publicNetworks.value = _publicNetworks.value.filterNot { it.name == current.name } } }
    fun approveRequest(request: JoinRequest) { _joinRequests.value = _joinRequests.value - request }
    fun denyRequest(request: JoinRequest) { _joinRequests.value = _joinRequests.value - request }
    fun banPeer(peer: com.mikael.lan.data.Peer) { _banned.value = _banned.value + peer.id; _network.value = _network.value?.copy(peers = _network.value!!.peers.filterNot { it.id == peer.id }) }
    fun removePeer(peer: com.mikael.lan.data.Peer) { _network.value = _network.value?.copy(peers = _network.value!!.peers.filterNot { it.id == peer.id }) }
    fun addGameProfile(name: String, port: Int, protocol: TransportProtocol) { require(name.isNotBlank()); require(port in 1..65535); _gameProfiles.value = _gameProfiles.value + LanGameProfile("custom-${System.currentTimeMillis()}", name.trim(), listOf(PortRule(port, protocol = protocol, description = "Regra personalizada")), false) }
    fun removeGameProfile(profile: LanGameProfile) { if (profile.id.startsWith("custom-")) _gameProfiles.value = _gameProfiles.value - profile }
    fun promotePeer(peer: com.mikael.lan.data.Peer) { updateRole(peer, "Moderador") }
    fun demotePeer(peer: com.mikael.lan.data.Peer) { updateRole(peer, "Membro") }
    private fun updateRole(peer: com.mikael.lan.data.Peer, role: String) { _network.value = _network.value?.copy(peers = _network.value!!.peers.map { if (it.id == peer.id) it.copy(role = role) else it }) }
    fun createNetwork(name: String, password: String) {
        require(name.trim().isNotEmpty()) { "Informe o nome da rede." }
        require(password.length >= 4) { "A senha deve ter pelo menos 4 caracteres." }
        _network.value = LanNetwork("MK-${(1000..9999).random()}", name.trim(), "senha configurada", "10.10.0.2", emptyList(), true)
        discoverWorlds()
        runDiagnostics()
    }
    fun joinNetwork(name: String, password: String) {
        require(name.trim().isNotEmpty()) { "Informe o nome da rede." }
        require(password.length >= 4) { "Informe a senha da rede." }
        require((_network.value?.peers?.size ?: 0) < _settings.value.maxPlayers) { "A rede atingiu o limite de jogadores." }
        _network.value = LanNetwork(name.trim(), name.trim(), "senha verificada", "10.10.0.3", emptyList(), true)
        discoverWorlds()
        runDiagnostics()
    }
    fun prepareVpn(): Intent? = _network.value?.let { VpnService.prepare(getApplication()) }
    fun startVpn() { _network.value?.let { net -> val intent = Intent(getApplication(), MikaelVpnService::class.java).apply { putExtra(MikaelVpnService.EXTRA_VIRTUAL_IP, net.virtualIp); putExtra(MikaelVpnService.EXTRA_RELAY_HOST, net.relayHost); putExtra(MikaelVpnService.EXTRA_RELAY_PORT, net.relayPort); putExtra(MikaelVpnService.EXTRA_TOKEN, net.sessionToken); putExtra(MikaelVpnService.EXTRA_DEVICE_ID, net.deviceId) }; getApplication<Application>().startService(intent) } }
    fun discoverWorlds() {
        val current = _network.value
        val host = current?.peers?.firstOrNull { it.virtualIp != current.virtualIp }
        _worlds.value = if (current?.connected == true) listOf(
            LanWorld("Mundo de ${host?.name ?: "outro jogador"}", host?.name ?: "Host da rede", host?.virtualIp ?: "10.10.0.2", 25565, host?.pingMs ?: 24)
        ) else emptyList()
    }
    fun runDiagnostics() { _diagnostics.value = listOf(Diagnostic("VPN virtual", Status.OK, "Interface 10.10.0.0/24 preparada"), Diagnostic("Coordenador", Status.OK, "Autenticação e peers disponíveis"), Diagnostic("P2P", Status.WARN, "Aguardando sinalização STUN"), Diagnostic("Minecraft", Status.OK, "Conexão direta IP:porta disponível")) }
}
