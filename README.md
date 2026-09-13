# MikaelLAN

MVP Android em Kotlin para criar uma rede virtual sem root e permitir que jogadores em redes diferentes usem comunicação por IP para jogos LAN, com foco em Minecraft Java Edition.

## O que já está implementado

- Projeto Android nativo em Kotlin, compatível com Android 10+ e ARM64.
- Interface Compose com criar rede, entrar em rede, lista de jogadores, IP virtual, conexão direta e diagnóstico.
- `VpnService` real, sem root, configurando a interface `10.10.0.0/24`.
- Modelos de rede/peer e cliente HTTP para um coordenador.
- Servidor de coordenação mínimo em Node.js com criação de rede, senha armazenada somente como hash, entrada autenticada, registro de peers e endpoint de health.
- Arquitetura modular para adicionar STUN, hole punching e relay sem acoplar essas funções à UI.

## Arquitetura

`Android UI → MikaelVpnService → transporte P2P/relay → coordenador`

O coordenador deve somente autenticar, registrar peers e trocar candidatos de conexão. O tráfego do jogo deve preferir P2P; relay é fallback. O transporte de pacotes ainda é o próximo incremento: a interface TUN já é criada, mas o encaminhamento de cada pacote para peers (incluindo criptografia, STUN e relay) precisa ser implementado antes de declarar o sistema pronto para partidas reais.

## Compilar o APK

1. Instale JDK 17 e Android SDK com API 35.
2. Abra a pasta no Android Studio e sincronize o Gradle, ou gere o wrapper com `gradle wrapper --gradle-version 8.7` em uma máquina com Gradle instalado.
3. Execute `./gradlew assembleDebug`.
4. Instale `app/build/outputs/apk/debug/app-debug.apk` em dois dispositivos Android 10+.

O primeiro uso de uma VPN Android exige a confirmação do sistema. Em Minecraft, use `Multiplayer > Direct Connection` com o IP virtual do host e a porta anunciada pelo mundo LAN (frequentemente `25565`, mas deve ser conferida no jogo).

## Executar o coordenador

```bash
cd coordinator
npm start
```

Para produção, coloque HTTPS na frente do serviço, use armazenamento persistente apropriado, tokens com expiração, rate limiting, logs mínimos e um relay separado. O endereço padrão no cliente é um placeholder; altere `CoordinatorClient` para o domínio publicado.

## Próximos incrementos necessários

1. Implementar transporte UDP criptografado entre peers e copiar pacotes TUN para o peer correto.
2. Integrar STUN e hole punching; marcar `transport` como `P2P` ou `RELAY` conforme o caminho.
3. Criar relay opcional com limites e autenticação.
4. Implementar descoberta multicast/broadcast de Minecraft quando suportada e descoberta alternativa por lista.
5. Adicionar testes instrumentados em dois dispositivos e validação real com Minecraft Java.

Este repositório é uma base funcional de MVP e não finge que a VPN sozinha encaminha tráfego pela Internet: sem o transporte P2P/relay, a interface virtual não pode entregar uma partida real entre redes distintas.
