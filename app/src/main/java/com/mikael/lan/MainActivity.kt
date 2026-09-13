package com.mikael.lan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikael.lan.data.Status
import com.mikael.lan.ui.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MikaelLanApp() } }
}

@Composable fun MikaelLanApp(vm: MainViewModel = viewModel()) {
    val network by vm.network.collectAsState(); val diagnostics by vm.diagnostics.collectAsState(); var screen by remember { mutableStateOf("home") }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8D91FF), background = Color(0xFF10111A), surface = Color(0xFF1A1B27))) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(20.dp)) {
            Text("MikaelLAN", style = MaterialTheme.typography.headlineLarge); Text("LAN virtual para jogar pela Internet", color = Color.LightGray)
            Spacer(Modifier.height(18.dp))
            when (screen) {
                "home" -> Home(network?.name, network?.peers?.size ?: 0, { screen = "create" }, { screen = "join" }, { screen = "network" }, { screen = "diagnostics" })
                "create" -> Create({ vm.createNetwork(it); screen = "network" }, { screen = "home" })
                "join" -> Join({ id, pass -> vm.joinNetwork(id, pass); screen = "network" }, { screen = "home" })
                "network" -> NetworkScreen(network, vm, { screen = "home" }, { screen = "diagnostics" })
                else -> Diagnostics(diagnostics, { screen = "home" })
            }
        } }
    }
}

@Composable private fun Home(name: String?, count: Int, create: () -> Unit, join: () -> Unit, networks: () -> Unit, diagnostics: () -> Unit) { StatusCard(if (name == null) "Não conectado" else "Conectado", name ?: "Nenhuma rede ativa", count); Spacer(Modifier.height(18.dp)); Button(create, Modifier.fillMaxWidth()) { Text("CRIAR REDE") }; Spacer(Modifier.height(10.dp)); OutlinedButton(join, Modifier.fillMaxWidth()) { Text("ENTRAR EM REDE") }; TextButton(networks, Modifier.fillMaxWidth()) { Text("MINHAS REDES") }; TextButton(diagnostics, Modifier.fillMaxWidth()) { Text("DIAGNÓSTICO") } }

@Composable private fun Create(done: (String) -> Unit, back: () -> Unit) { var name by remember { mutableStateOf("MinhaRede") }; Title("Criar rede", back); OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome da rede") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp)); Text("Um ID e uma senha forte serão gerados para compartilhar com seus amigos.", color = Color.LightGray); Spacer(Modifier.height(18.dp)); Button({ done(name) }, Modifier.fillMaxWidth()) { Text("CRIAR E CONECTAR") } }
@Composable private fun Join(done: (String, String) -> Unit, back: () -> Unit) { var id by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }; Title("Entrar em rede", back); OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("ID da rede") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Código/senha") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(18.dp)); Button({ done(id, pass) }, Modifier.fillMaxWidth()) { Text("CONECTAR") } }

@Composable private fun NetworkScreen(n: com.mikael.lan.data.LanNetwork?, vm: MainViewModel, back: () -> Unit, diag: () -> Unit) { Title(n?.name ?: "Rede", back); StatusCard("Conectado", "IP virtual: ${n?.virtualIp ?: "-"}", n?.peers?.size ?: 0); Spacer(Modifier.height(14.dp)); Text("Jogadores", style = MaterialTheme.typography.titleLarge); n?.peers?.let { LazyColumn(Modifier.fillMaxWidth()) { items(it) { p -> Text("● ${p.name} — ${p.virtualIp} — ${p.pingMs ?: "?"} ms (${p.transport})", Modifier.padding(vertical = 8.dp)) } } }; Text("Conectar diretamente: ${n?.virtualIp ?: "10.10.0.x"}:25565", color = Color.LightGray); Spacer(Modifier.height(12.dp)); Button({ vm.runDiagnostics(); diag() }, Modifier.fillMaxWidth()) { Text("DIAGNÓSTICO") }; TextButton(back, Modifier.fillMaxWidth()) { Text("SAIR DA REDE") } }
@Composable private fun Diagnostics(items: List<com.mikael.lan.data.Diagnostic>, back: () -> Unit) { Title("Diagnóstico", back); if (items.isEmpty()) Text("Nenhum diagnóstico executado.") else LazyColumn { items(items) { d -> val icon = if (d.status == Status.OK) "✓" else if (d.status == Status.WARN) "!" else "×"; Text("$icon ${d.label}: ${d.detail}", Modifier.padding(vertical = 9.dp)) } }; Spacer(Modifier.height(14.dp)); Text("A descoberta automática multicast pode ser limitada pelo Android; use IP virtual + porta quando necessário.", color = Color.LightGray) }
@Composable private fun StatusCard(status: String, network: String, count: Int) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("● $status", color = Color(0xFF66E38C)); Text("Rede: $network"); Text("Jogadores: $count") } } }
@Composable private fun Title(title: String, back: () -> Unit) { TextButton(back) { Text("← Voltar") }; Text(title, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(12.dp)) }
