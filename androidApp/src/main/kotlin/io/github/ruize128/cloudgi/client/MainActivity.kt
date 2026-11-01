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
            val hosts by connector.discoveredHosts.collectAsState()
            val discoveryStatus by connector.discoveryStatus.collectAsState()
            val searching by connector.isSearching.collectAsState()
            LaunchedEffect(connector) { connector.refreshDiscovery() }
            App(platformName = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
                connectionState = status, isConnecting = connecting,
                discoveredHosts = hosts, discoveryStatus = discoveryStatus, isSearching = searching,
                onRefreshSearch = { if (!connecting && !searching) scope.launch { connector.refreshDiscovery() } },
                onStartGame = {
                    if (!connecting) scope.launch {
                        connecting = true
                        try {
                            val intent = connector.connect { message -> android.util.Log.i("CloudGI", message); withContext(Dispatchers.Main) { status = message } }
                            startActivity(intent)
                            status = "已打开串流页面；断开连接后，Host 会自动退出游戏。"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            android.util.Log.w("CloudGI", "Connection failed: ${error.javaClass.simpleName}: ${error.message}")
                            status = error.message ?: "连接失败，请重试。"
                        } finally { connecting = false }
                    }
                })
        }
    }
}
