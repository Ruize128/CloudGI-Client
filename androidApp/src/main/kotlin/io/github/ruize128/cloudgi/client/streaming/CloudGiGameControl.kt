package io.github.ruize128.cloudgi.client.streaming

import android.content.Context
import android.util.Base64
import com.limelight.binding.PlatformBinding
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Signs control operations with the existing paired Moonlight device key. */
internal class CloudGiGameControl(context: Context, private val address: String, private val port: Int, certificate: X509Certificate) {
    private val crypto = PlatformBinding.getCryptoProvider(context.applicationContext)
    private val fingerprint = MessageDigest.getInstance("SHA-256").digest(crypto.clientCertificate.encoded)
        .joinToString("") { "%02X".format(it.toInt() and 0xff) }
    private val http: OkHttpClient

    init {
        val expected = certificate.encoded
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = throw CertificateException("Client trust not supported")
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                if (chain.isEmpty() || !MessageDigest.isEqual(expected, chain[0].encoded)) throw CertificateException("Host identity changed")
            }
        }
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        http = OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, trust).hostnameVerifier { _, _ -> true }
            .connectTimeout(2, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(14, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).proxy(java.net.Proxy.NO_PROXY).build()
    }

    fun request(operation: String, post: Boolean = true): JSONObject {
        val url = HttpUrl.Builder().scheme("https").host(address).port(port + 2)
            .addPathSegment("cloudgi").addPathSegments(operation).build()
        val method = if (post) "POST" else "GET"
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val payload = "CLOUDGI-GAME-1\n$method\n${url.encodedPath()}\n$timestamp\n$nonce".toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(crypto.clientPrivateKey); update(payload); sign() }
        val request = Request.Builder().url(url).header("X-CloudGI-Client", "1")
            .header("X-CloudGI-Device", fingerprint).header("X-CloudGI-Time", timestamp)
            .header("X-CloudGI-Nonce", nonce).header("X-CloudGI-Signature", Base64.encodeToString(signature, Base64.NO_WRAP))
        if (post) request.post(RequestBody.create(null, ByteArray(0)))
        http.newCall(request.build()).execute().use { response ->
            val body = response.body()?.string() ?: throw IOException("Host 未返回游戏状态。")
            val data = try { JSONObject(body) } catch (_: Exception) { throw IOException("Host 游戏接口返回了无效响应。") }
            if (!response.isSuccessful) throw IOException(data.optString("error").ifBlank { "游戏请求失败（${response.code()}）。" })
            return data
        }
    }
}
