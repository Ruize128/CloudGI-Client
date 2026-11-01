package io.github.ruize128.cloudgi.client.streaming

import android.os.Bundle
import android.util.Log
import com.limelight.Game
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean

/** Moonlight keeps its original player; CloudGI adds a lease for that player's actual lifetime. */
class CloudGiGameActivity : Game() {
    companion object { const val EXTRA_REQUEST_ID = "cloudgi.requestId" }
    private val streamScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ended = AtomicBoolean(false)
    private var control: CloudGiGameControl? = null
    private var operationId: String? = null
    private var heartbeat: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        operationId = intent.getStringExtra(EXTRA_REQUEST_ID)
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(
            ByteArrayInputStream(requireNotNull(intent.getByteArrayExtra(EXTRA_SERVER_CERT)))) as X509Certificate
        control = CloudGiGameControl(applicationContext, requireNotNull(intent.getStringExtra(EXTRA_HOST)),
            intent.getIntExtra(EXTRA_PORT, 0), certificate)
        super.onCreate(savedInstanceState)
    }

    @Synchronized override fun connectionStarted() {
        super.connectionStarted()
        if (ended.get() || heartbeat != null) return
        val client = control ?: return
        val id = operationId ?: return
        heartbeat = streamScope.launch {
            Log.i("CloudGI", "Stream connected; game lease heartbeat started")
            while (isActive) {
                try { client.request("game/heartbeat/$id") }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { Log.w("CloudGI", "Game lease heartbeat failed; Host will clean up if connection stays unavailable") }
                delay(2000)
            }
        }
    }

    private fun endGameSession() {
        if (!ended.compareAndSet(false, true)) return
        heartbeat?.cancel()
        val client = control ?: return
        val id = operationId ?: return
        // Independent of Activity teardown; killed processes are covered by Host's lease timeout.
        CoroutineScope(Dispatchers.IO).launch {
            try { client.request("game/disconnect/$id"); Log.i("CloudGI", "Disconnected; Host game cleanup completed") }
            catch (_: Exception) { Log.w("CloudGI", "Disconnect notification failed; Host lease timeout will clean up") }
        }
    }

    override fun onStop() { endGameSession(); super.onStop() }
    override fun connectionTerminated(errorCode: Int) { endGameSession(); super.connectionTerminated(errorCode) }
    override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) { endGameSession(); super.stageFailed(stage, portFlags, errorCode) }
    override fun onDestroy() { endGameSession(); streamScope.cancel(); super.onDestroy() }
}
