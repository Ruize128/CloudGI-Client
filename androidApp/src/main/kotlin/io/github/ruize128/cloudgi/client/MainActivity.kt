package io.github.ruize128.cloudgi.client

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import io.github.ruize128.cloudgi.client.streaming.CloudGiConnector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val connector = remember { CloudGiConnector(applicationContext) }
            val scope = rememberCoroutineScope()
            var connecting by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("") }
            var connectionError by remember { mutableStateOf<String?>(null) }
            val selectedHostId by connector.selectedHostId.collectAsState()
            val hosts by connector.discoveredHosts.collectAsState()
            val discoveryStatus by connector.discoveryStatus.collectAsState()
            val searching by connector.isSearching.collectAsState()
            LaunchedEffect(connector) { connector.refreshDiscovery() }
            App(platformName = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
                connectionState = status, connectionError = connectionError, isConnecting = connecting,
                selectedHostId = selectedHostId,
                onSelectHost = { id ->
                    connector.selectHost(id)
                    connectionError = null
                    status = "已选择主机，点击启动游戏连接。"
                },
                discoveredHosts = hosts, discoveryStatus = discoveryStatus, isSearching = searching,
                onRefreshSearch = { if (!connecting && !searching) scope.launch { connector.refreshDiscovery() } },
                onStartGame = {
                    if (!connecting) scope.launch {
                        connecting = true
                        connectionError = null
                        try {
                            val intent = connector.connect { message -> android.util.Log.i("CloudGI", message); withContext(Dispatchers.Main) { status = message } }
                            startActivity(intent)
                            status = "已打开串流页面；断开连接后，Host 会自动退出游戏。"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            android.util.Log.w("CloudGI", "Connection failed: ${error.javaClass.simpleName}: ${error.message}")
                            connectionError = "阶段：$status\n" +
                                (error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName)
                            status = "连接未完成，请查看下方错误。"
                        } finally { connecting = false }
                    }
                })
        }
    }
}
