package io.github.ruize128.cloudgi.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun App(
    platformName: String,
    onStartGame: (() -> Unit)? = null,
    connectionState: String = "",
    isConnecting: Boolean = false,
    discoveredHosts: List<DiscoveredHost> = emptyList(),
    discoveryStatus: String = "Android 端提供局域网搜索",
    isSearching: Boolean = false,
    onRefreshSearch: (() -> Unit)? = null,
) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF315CDA),
            background = Color(0xFFF5F7FB),
            surface = Color.White,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 660.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "CLOUDGI / CLIENT",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "CloudGI Client",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = { onStartGame?.invoke() }, enabled = onStartGame != null && !isConnecting,
                    modifier = Modifier.fillMaxWidth()) { Text(if (isConnecting) "正在连接…" else "启动游戏") }
                if (isConnecting) CircularProgressIndicator()
                Text(connectionState.ifEmpty { if (onStartGame != null) "自动发现主机并连接桌面串流" else "串流连接目前支持 Android" })
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("发现的主机 · 调试", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { onRefreshSearch?.invoke() },
                                enabled = onRefreshSearch != null && !isSearching && !isConnecting) {
                                Text(if (isSearching) "搜索中…" else "刷新搜索")
                            }
                        }
                        Text(discoveryStatus, style = MaterialTheme.typography.bodySmall)
                        Text("mDNS / _nvstream._tcp.local. · 显示所有发现的 Sunshine 服务",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (discoveredHosts.isEmpty()) Text("暂无服务记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        for (host in discoveredHosts) {
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(host.cloudGiName ?: host.serviceName, fontWeight = FontWeight.SemiBold)
                                    Text("${host.source} · ${host.serviceName}", style = MaterialTheme.typography.labelSmall)
                                    Text(if (host.address != null) "${host.address}:${host.sunshinePort}  /  CloudGI :${host.sunshinePort!! + 2}"
                                        else "地址和端口尚未解析", style = MaterialTheme.typography.bodySmall)
                                    Text(host.status, style = MaterialTheme.typography.bodySmall,
                                        color = if (host.canConnect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Text(
                    "在你的设备上，走近 CloudGI。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("基本信息", style = MaterialTheme.typography.titleMedium)
                        Info("版本", "0.1.0")
                        Info("当前环境", platformName)
                        Info("目标平台", "Android 优先，随后 iOS、macOS")
                        Info("当前阶段", "桌面串流 · 自动配对")
                    }
                }
                Spacer(Modifier.height(4.dp))

            }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
