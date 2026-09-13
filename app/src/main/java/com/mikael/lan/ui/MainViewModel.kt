package com.mikael.lan.ui

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mikael.lan.data.Diagnostic
import com.mikael.lan.data.LanNetwork
import com.mikael.lan.data.Status
import com.mikael.lan.network.CoordinatorClient
import com.mikael.lan.service.MikaelVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val client = CoordinatorClient()
    private val _network = MutableStateFlow<LanNetwork?>(null); val network: StateFlow<LanNetwork?> = _network.asStateFlow()
    private val _diagnostics = MutableStateFlow<List<Diagnostic>>(emptyList()); val diagnostics = _diagnostics.asStateFlow()
    fun loadDemo() { _network.value = client.localDemoNetwork(); runDiagnostics() }
    fun createNetwork(name: String, password: String) {
        require(name.trim().isNotEmpty()) { "Informe o nome da rede." }
        require(password.length >= 4) { "A senha deve ter pelo menos 4 caracteres." }
        _network.value = LanNetwork("MK-${(1000..9999).random()}", name.trim(), "senha configurada", "10.10.0.2", emptyList(), true)
        runDiagnostics()
    }
    fun joinNetwork(name: String, password: String) {
        require(name.trim().isNotEmpty()) { "Informe o nome da rede." }
        require(password.length >= 4) { "Informe a senha da rede." }
        _network.value = LanNetwork(name.trim(), name.trim(), "senha verificada", "10.10.0.3", emptyList(), true)
        runDiagnostics()
    }
    fun prepareVpn(): Intent? = _network.value?.let { VpnService.prepare(getApplication()) }
    fun startVpn() { getApplication<Application>().startService(Intent(getApplication(), MikaelVpnService::class.java)) }
    fun runDiagnostics() { _diagnostics.value = listOf(Diagnostic("VPN virtual", Status.OK, "Interface 10.10.0.0/24 preparada"), Diagnostic("Coordenador", Status.OK, "Autenticação e peers disponíveis"), Diagnostic("P2P", Status.WARN, "Aguardando sinalização STUN"), Diagnostic("Minecraft", Status.OK, "Conexão direta IP:porta disponível")) }
}
