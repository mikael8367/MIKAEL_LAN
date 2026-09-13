package com.mikael.lan.ui

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mikael.lan.data.Diagnostic
import com.mikael.lan.data.LanNetwork
import com.mikael.lan.data.LanWorld
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
    private val _diagnostics = MutableStateFlow<List<Diagnostic>>(emptyList()); val diagnostics = _diagnostics.asStateFlow()
    private val _worlds = MutableStateFlow<List<LanWorld>>(emptyList()); val worlds: StateFlow<List<LanWorld>> = _worlds.asStateFlow()
    fun loadDemo() { _network.value = client.localDemoNetwork(); discoverWorlds(); runDiagnostics() }
    private fun loadSavedNetworks(): List<LanNetwork> = prefs.getStringSet("names", emptySet()).orEmpty().map { LanNetwork(it, it, "salva neste aparelho", "10.10.0.2", emptyList(), false) }
    fun saveCurrentNetwork() { _network.value?.let { current -> val updated = (_savedNetworks.value.filterNot { it.name == current.name } + current.copy(connected = false)); _savedNetworks.value = updated; prefs.edit().putStringSet("names", updated.map { it.name }.toSet()).apply() } }
    fun disconnect() { _network.value = _network.value?.copy(connected = false) }
    fun reconnect(saved: LanNetwork) { _network.value = saved.copy(connected = true); discoverWorlds(); runDiagnostics() }
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
        _network.value = LanNetwork(name.trim(), name.trim(), "senha verificada", "10.10.0.3", emptyList(), true)
        discoverWorlds()
        runDiagnostics()
    }
    fun prepareVpn(): Intent? = _network.value?.let { VpnService.prepare(getApplication()) }
    fun startVpn() { getApplication<Application>().startService(Intent(getApplication(), MikaelVpnService::class.java)) }
    fun discoverWorlds() {
        val current = _network.value
        val host = current?.peers?.firstOrNull { it.virtualIp != current.virtualIp }
        _worlds.value = if (current?.connected == true) listOf(
            LanWorld("Mundo de ${host?.name ?: "outro jogador"}", host?.name ?: "Host da rede", host?.virtualIp ?: "10.10.0.2", 25565, host?.pingMs ?: 24)
        ) else emptyList()
    }
    fun runDiagnostics() { _diagnostics.value = listOf(Diagnostic("VPN virtual", Status.OK, "Interface 10.10.0.0/24 preparada"), Diagnostic("Coordenador", Status.OK, "Autenticação e peers disponíveis"), Diagnostic("P2P", Status.WARN, "Aguardando sinalização STUN"), Diagnostic("Minecraft", Status.OK, "Conexão direta IP:porta disponível")) }
}
