package io.github.ruize128.cloudgi.client.streaming

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Base64
import com.limelight.nvstream.mdns.MdnsComputer
import com.limelight.nvstream.mdns.MdnsDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsDiscoveryListener
import com.limelight.nvstream.mdns.NsdManagerDiscoveryAgent
import com.limelight.nvstream.mdns.JmDNSDiscoveryAgent
import com.limelight.Game
import com.limelight.binding.PlatformBinding
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.ruize128.cloudgi.client.DiscoveredHost
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Uses Moonlight's pairing and native player; the PIN never becomes part of the UI or logs. */
class CloudGiConnector(private val context: Context) {
    private val saved = context.getSharedPreferences("cloudgi-hosts", Context.MODE_PRIVATE)
    private val clientId = saved.getString("clientId", null) ?: UUID.randomUUID().toString().replace("-", "").also {
        check(saved.edit().putString("clientId", it).commit())
    }
    private val _discoveredHosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discoveredHosts = _discoveredHosts.asStateFlow()
    private val _discoveryStatus = MutableStateFlow("尚未搜索")
    val discoveryStatus = _discoveryStatus.asStateFlow()
    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()
    private val discoveryGate = Mutex()
    private val observations = linkedMapOf<String, DiscoveredHost>()
    private val observationLock = Any()
    private var cachedHosts = emptyList<Host>()
    private var lastScanTime = 0L

    private fun updateObservation(key: String, update: (DiscoveredHost?) -> DiscoveredHost) {
        synchronized(observationLock) {
            observations[key] = update(observations[key])
            _discoveredHosts.value = observations.values.toList()
        }
    }

    private fun updateEndpoint(endpoint: Endpoint, status: String, host: Host? = null) {
        synchronized(observationLock) {
            for ((key, value) in observations.toMap()) {
                if (value.address == endpoint.address && value.sunshinePort == endpoint.port) {
                    observations[key] = value.copy(status = status, cloudGiName = host?.name, canConnect = host != null)
                }
            }
            _discoveredHosts.value = observations.values.toList()
        }
        android.util.Log.i("CloudGI", "Discovery probe ${endpoint.address}:${endpoint.port}: $status")
    }

    suspend fun refreshDiscovery() { discoverCandidates(force = true) }

    private data class Endpoint(val address: String, val port: Int)
    private data class Host(val endpoint: Endpoint, val id: String, val name: String, val httpsPort: Int, val certificate: X509Certificate, val gameControl: String, val disconnectPolicy: String)

    suspend fun connect(progress: suspend (String) -> Unit): Intent = withContext(Dispatchers.IO) {
        progress("正在自动发现 CloudGI 主机…")
        val host = discover()
        if (host.gameControl != "signed-request-v1") throw IOException("此 Host 尚不支持手机启动游戏，请更新电脑端 CloudGI Host。")
        if (host.disconnectPolicy != "close-game-lease-v1") throw IOException("请更新 Host，以支持断开连接后自动退出游戏。")
        progress("已发现 ${host.name}，正在连接…")
        android.util.Log.i("CloudGI", "CloudGI host discovered: ${host.name}")
        val crypto = PlatformBinding.getCryptoProvider(context)
        val http = NvHTTP(ComputerDetails.AddressTuple(host.endpoint.address, host.endpoint.port), host.httpsPort, clientId, host.certificate, crypto)
        val serverInfo = http.getServerInfo(true)
        val details = http.getComputerDetails(serverInfo)
        if (details.uuid != host.id) throw IOException("主机身份不一致，请重试。")
        if (http.getPairState(serverInfo) != PairingManager.PairState.PAIRED) {
            progress("正在自动配对 ${host.name}…")
            val pin = PairingManager.generatePinString()
            val manager = PairingManager(http, crypto)
            supervisorScope {
                val handshake = async(Dispatchers.IO) { manager.pair(serverInfo, pin) }
                try {
                    val body = JSONObject().put("clientId", clientId).put("pin", pin)
                        .put("deviceName", "CloudGI ${Build.MODEL}".take(100))
                    val request = Request.Builder().url(controlUrl(host.endpoint, "pair"))
                        .header("X-CloudGI-Client", "1")
                        .post(RequestBody.create(MediaType.parse("application/json"), body.toString())).build()
                    tlsClient(host.certificate, 35).newCall(request).execute().use { response ->
                        if (!response.isSuccessful || !JSONObject(response.body()!!.string()).optBoolean("paired"))
                            throw IOException("自动配对未完成，请重试连接（${response.code()}）。")
                    }
                    if (handshake.await() != PairingManager.PairState.PAIRED) throw IOException("Sunshine 配对握手未完成，请重试。")
                    if (!MessageDigest.isEqual(manager.pairedCert.encoded, host.certificate.encoded)) throw IOException("配对证书与发现的主机不一致。")
                } catch (error: Exception) {
                    // Close the pending long-poll request when the automatic authorization failed.
                    runCatching { http.unpair() }
                    handshake.cancel()
                    throw error
                }
            }
        }
        check(saved.edit().putString("hostId", host.id).putString("address", host.endpoint.address)
            .putInt("port", host.endpoint.port)
            .putString("certificate-${host.id}", Base64.encodeToString(host.certificate.encoded, Base64.NO_WRAP)).commit())
        val desktop = http.appList.firstOrNull { it.appName == "Desktop" }
            ?: throw IOException("CloudGI 主机未提供 Desktop 串流入口。")
        val control = CloudGiGameControl(context, host.endpoint.address, host.endpoint.port, host.certificate)
        val operationId = startGame(control, progress)
        progress("游戏进程已运行，正在连接串流…")
        // Keep the physical display and game resolution unchanged. This is the initial video encoding preset.
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("list_resolution", "1280x720").putString("list_fps", "60")
            .putInt("seekbar_bitrate_kbps", 10000).putBoolean("checkbox_enable_hdr", false)
            .putBoolean("checkbox_show_onscreen_controls", false).apply()
        android.util.Log.i("CloudGI", "Opening native Desktop stream")
        // Host now owns a lease: a failed launch or killed client will be cleaned up after its timeout.
        control.request("game/stream/$operationId", post = true)
        Intent(context, CloudGiGameActivity::class.java).apply {
            putExtra(CloudGiGameActivity.EXTRA_REQUEST_ID, operationId)
            putExtra(Game.EXTRA_HOST, host.endpoint.address)
            putExtra(Game.EXTRA_PORT, host.endpoint.port)
            putExtra(Game.EXTRA_HTTPS_PORT, host.httpsPort)
            putExtra(Game.EXTRA_APP_NAME, desktop.appName)
            putExtra(Game.EXTRA_APP_ID, desktop.appId)
            putExtra(Game.EXTRA_APP_HDR, false)
            putExtra(Game.EXTRA_UNIQUEID, clientId)
            putExtra(Game.EXTRA_PC_UUID, host.id)
            putExtra(Game.EXTRA_PC_NAME, host.name)
            putExtra(Game.EXTRA_SERVER_CERT, host.certificate.encoded)
        }
    }

    private suspend fun startGame(control: CloudGiGameControl, progress: suspend (String) -> Unit): String {
        progress("正在请求 Host 启动游戏…")
        // A request ID identifies one button press. Polling and HTTP disconnects never resubmit a launch.
        val requestId = UUID.randomUUID().toString().replace("-", "")
        var snapshot = control.request("game/start/$requestId", post = true)
        val operationId = snapshot.getString("requestId")
        if (!operationId.matches(Regex("[a-fA-F0-9]{32}"))) throw IOException("Host 返回了无效的启动请求标识。")
        val deadline = android.os.SystemClock.elapsedRealtime() + 120_000
        while (true) {
            currentCoroutineContext().ensureActive()
            when (snapshot.getString("state")) {
                "running" -> {
                    if (snapshot.optInt("processId", 0) <= 0) throw IOException("Host 未返回有效的游戏进程。")
                    android.util.Log.i("CloudGI", "Host confirmed game process; opening stream")
                    return operationId
                }
                "failed", "exited" -> throw IOException(snapshot.optString("error").ifBlank { "游戏未能启动，请查看 Host。" })
                "starting" -> progress("正在启动游戏；如电脑显示 Windows 授权提示，请在电脑上确认…")
                else -> throw IOException("Host 返回了未知的游戏状态。")
            }
            if (android.os.SystemClock.elapsedRealtime() >= deadline)
                throw IOException("等待游戏启动超时，请查看电脑上的授权提示和 Host 状态；游戏不会因超时被关闭。")
            delay(1000)
            snapshot = control.request("game/status/$operationId", post = false)
        }
    }

    private fun controlUrl(endpoint: Endpoint, operation: String) = HttpUrl.Builder().scheme("https")
        .host(endpoint.address).port(endpoint.port + 2).addPathSegment("cloudgi").addPathSegments(operation).build()

    private fun tlsClient(expected: X509Certificate?, timeout: Long, observed: ((X509Certificate) -> Unit)? = null): OkHttpClient {
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = throw CertificateException("Client trust not supported")
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                val leaf = chain.firstOrNull() ?: throw CertificateException("Missing host certificate")
                if (expected != null && !MessageDigest.isEqual(expected.encoded, leaf.encoded)) throw CertificateException("Host identity changed")
                observed?.invoke(leaf)
            }
        }
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        // Trust-on-first-use is limited to the explicitly selected local CloudGI endpoint. Later connections pin this certificate.
        return OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, trust).hostnameVerifier { _, _ -> true }
            .connectTimeout(2, TimeUnit.SECONDS).readTimeout(timeout, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).proxy(java.net.Proxy.NO_PROXY).build()
    }

    private fun probe(endpoint: Endpoint): Host? {
        updateEndpoint(endpoint, "检查 CloudGI HTTPS 接口 :${endpoint.port + 2}…")
        return try {
            val address = InetAddress.getByName(endpoint.address)
            if (!address.isSiteLocalAddress && !address.isLinkLocalAddress && !address.isLoopbackAddress)
                throw IOException("跳过非局域网地址")
            var certificate: X509Certificate? = null
            tlsClient(null, 3) { certificate = it }.newCall(Request.Builder().url(controlUrl(endpoint, "info")).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("控制接口返回 HTTP ${response.code()}")
                val data = JSONObject(response.body()!!.string())
                if (data.optInt("protocolVersion") != 1) throw IOException("非 CloudGI 控制协议")
                if (data.optInt("sunshinePort") != endpoint.port) throw IOException("控制接口的 Sunshine 端口不匹配")
                val id = data.getString("hostId")
                val stored = saved.getString("certificate-$id", null)
                if (stored != null && !MessageDigest.isEqual(Base64.decode(stored, Base64.NO_WRAP), certificate!!.encoded))
                    throw CertificateException("主机证书变化，已停止连接")
                val host = Host(endpoint, id, data.getString("name"), data.getInt("sunshineHttpsPort"), certificate!!, data.optString("gameControl"), data.optString("disconnectPolicy"))
                updateEndpoint(endpoint, "CloudGI 接口验证通过", host)
                host
            }
        } catch (error: Exception) {
            val reason = when (error) {
                is java.net.SocketTimeoutException -> "控制接口连接或响应超时"
                is java.net.ConnectException -> "控制端口无法连接，可能是普通 Sunshine 或未放行网络"
                is javax.net.ssl.SSLException -> "HTTPS 握手失败：${error.message}"
                else -> "${error.javaClass.simpleName}：${error.message}"
            }
            updateEndpoint(endpoint, reason)
            null
        }
    }

    private suspend fun discover(): Host {
        val preferredId = saved.getString("hostId", null)
        val hosts = discoverCandidates(force = false)
        if (preferredId != null) return hosts.firstOrNull { it.id == preferredId }
            ?: throw IOException("未找到上次连接的 CloudGI 主机；下方列表显示了本次发现结果。")
        return hosts.firstOrNull() ?: throw IOException(
            if (_discoveredHosts.value.isEmpty()) "未收到 Sunshine 服务记录，请查看下方搜索状态。"
            else "已发现服务，但没有通过 CloudGI 接口检查的主机；请查看下方各主机的检查结果。")
    }

    private suspend fun discoverCandidates(force: Boolean): List<Host> = withContext(Dispatchers.IO) {
        discoveryGate.withLock {
            if (!force && System.currentTimeMillis() - lastScanTime < 2500) return@withLock cachedHosts
            _isSearching.value = true
            _discoveryStatus.value = "搜索中 · NSD + JmDNS · UDP 5353"
            synchronized(observationLock) { observations.clear(); _discoveredHosts.value = emptyList() }
            val hosts = java.util.concurrent.ConcurrentHashMap<String, Host>()
            try {
                coroutineScope {
                    val scope = this
                    val jobs = mutableListOf<Job>()
                    val seen = mutableSetOf<Endpoint>()
                    var accepting = true
                    fun listener(source: String) = object : MdnsDiscoveryListener {
                        override fun notifyServiceFound(name: String) {
                            synchronized(observationLock) {
                                if (!accepting) return
                                val key = "$source|$name"
                                if (observations.values.none { it.source == source && it.serviceName == name && it.address != null })
                                    updateObservation(key) { DiscoveredHost(key, name, source) }
                            }
                        }
                        override fun notifyComputerAdded(computer: MdnsComputer) {
                            val address = computer.localAddress ?: computer.ipv6Address ?: return
                            val endpoint = Endpoint(address.hostAddress ?: return, computer.port)
                            synchronized(observationLock) {
                                if (!accepting) return
                                observations.remove("$source|${computer.name}")
                                val key = "$source|${computer.name}|${endpoint.address}:${endpoint.port}"
                                val known = hosts.values.firstOrNull { it.endpoint == endpoint }
                                updateObservation(key) {
                                    DiscoveredHost(key, computer.name, source, endpoint.address, endpoint.port,
                                        known?.name, if (known != null) "CloudGI 接口验证通过" else "地址已解析，等待接口检查", known != null)
                                }
                                if (seen.add(endpoint)) jobs.add(scope.launch {
                                    probe(endpoint)?.let { hosts[it.id] = it }
                                })
                            }
                        }
                        override fun notifyDiscoveryFailure(error: Exception) {
                            _discoveryStatus.value = "$source 发现错误：${error.message}"
                            android.util.Log.w("CloudGI", "$source discovery: ${error.message}")
                        }
                    }
                    val agents = mutableListOf<MdnsDiscoveryAgent>()
                    if (Build.VERSION.SDK_INT >= 34) agents.add(NsdManagerDiscoveryAgent(context, listener("NSD")))
                    agents.add(JmDNSDiscoveryAgent(context, listener("JmDNS")))
                    val started = mutableListOf<MdnsDiscoveryAgent>()
                    try {
                        for (agent in agents) {
                            try { agent.startDiscovery(1000); started.add(agent) }
                            catch (error: Exception) { _discoveryStatus.value = "发现启动失败：${error.message}" }
                        }
                        delay(10000)
                    } finally {
                        synchronized(observationLock) { accepting = false }
                        for (agent in started) runCatching { agent.stopDiscovery() }
                    }
                    jobs.toList().joinAll()
                }
                cachedHosts = hosts.values.toList()
                lastScanTime = System.currentTimeMillis()
                val rows = _discoveredHosts.value
                _discoveryStatus.value = if (rows.isEmpty()) "搜索结束：没有收到 mDNS 服务记录，尚未进行 CloudGI 接口检查"
                    else "搜索结束：${rows.map { it.serviceName }.distinct().size} 个服务，${cachedHosts.size} 台 CloudGI 主机可连接"
                cachedHosts
            } finally { _isSearching.value = false }
        }
    }
}
