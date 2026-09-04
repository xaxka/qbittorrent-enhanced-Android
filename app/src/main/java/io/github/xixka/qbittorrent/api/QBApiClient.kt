package io.github.xixka.qbittorrent.api

import android.util.Log
import io.github.xixka.qbittorrent.data.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Response as RetrofitResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * OkHttp/Retrofit client for the qBittorrent Web API v2.
 *
 * Authentication uses the upstream SID cookie flow:
 *  1. POST /api/v2/auth/login with username/password
 *  2. keep the SID cookie in memory and attach it to every request
 *  3. when a call answers 403 (expired session), log in again once and retry
 *
 * A `Referer` header is always sent to satisfy the server's CSRF check,
 * mirroring the behaviour of the official WebUI.
 */
class QBApiClient(private val configProvider: () -> ServerConfig) {

    private val cookieJar = InMemoryCookieJar()

    @Volatile
    private var service: QBApiService? = null

    @Volatile
    private var serviceConfig: ServerConfig? = null

    @Volatile
    private var loggedIn = false

    fun reset() {
        synchronized(this) {
            service = null
            serviceConfig = null
            loggedIn = false
            cookieJar.clear()
        }
    }

    private fun currentService(): QBApiService {
        val cfg = configProvider()
        service?.let { existing ->
            if (serviceConfig == cfg) return existing
        }
        synchronized(this) {
            val cfgNow = configProvider()
            service?.let { existing ->
                if (serviceConfig == cfgNow) return existing
            }
            val created = buildService(cfgNow)
            service = created
            serviceConfig = cfgNow
            loggedIn = false
            cookieJar.clear()
            return created
        }
    }

    private fun buildService(cfg: ServerConfig): QBApiService {
        val baseUrl = cfg.baseUrl().toHttpUrlOrNull()
            ?: throw QBConnectException("Invalid server address: ${cfg.baseUrl()}")

        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(RefererInterceptor(cfg))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (cfg.trustAllCerts) {
            applyTrustAll(builder)
        }
        val http = builder.build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(http)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(QBApiService::class.java)
    }

    /**
     * Executes an API call, logging in first if needed and retrying exactly once
     * after a session expiry. Unsuccessful responses are surfaced as
     * [QBApiException]: the action endpoints are declared as
     * `Response<ResponseBody>` (Retrofit only throws for plain return types),
     * so without this check a rejected call — e.g. a missing required qB 5.x
     * parameter — would fail silently and the UI would believe it succeeded.
     */
    suspend fun <T> withAuth(block: suspend (QBApiService) -> T): T = withContext(Dispatchers.IO) {
        if (!loggedIn) login()
        try {
            val result = block(currentService())
            if (result.isAuthExpired()) {
                // the session cookie expired mid-call: re-login and retry once
                login()
                checked(block(currentService()))
            } else {
                checked(result)
            }
        } catch (e: HttpException) {
            if (e.code() == 403 || e.code() == 401) {
                login()
                checked(block(currentService()))
            } else {
                throw QBApiException("Server error HTTP ${e.code()}")
            }
        }
    }

    /** Fails the call when a Response-typed endpoint answered non-2xx. */
    private fun <T> checked(result: T): T {
        if (result is RetrofitResponse<*> && !result.isSuccessful) {
            throw QBApiException("Server error HTTP ${result.code()}")
        }
        return result
    }

    private fun Any?.isAuthExpired(): Boolean =
        this is RetrofitResponse<*> && (code() == 401 || code() == 403)

    private suspend fun login() = withContext(Dispatchers.IO) {
        val cfg = configProvider()
        if (!cfg.isConfigured) {
            throw QBConnectException("Server is not configured")
        }
        loggedIn = false
        cookieJar.clear()
        val api = currentService()
        val response = try {
            api.login(cfg.username, cfg.password)
        } catch (e: retrofit2.HttpException) {
            throw mapLoginHttpException(e)
        } catch (e: Exception) {
            throw QBConnectException("Cannot reach ${cfg.displayHost()}: ${e.message}", e)
        }
        if (!response.isSuccessful) {
            when (response.code()) {
                403 -> {
                    val body = response.errorBody()?.string().orEmpty()
                    throw QBAuthException(
                        if (body.contains("ban", ignoreCase = true) || body.contains("Too many", ignoreCase = true))
                            "Login banned: too many failed attempts, try again later"
                        else "Login failed (HTTP 403)"
                    )
                }
                else -> throw QBConnectException("Login failed (HTTP ${response.code()})")
            }
        }
        val body = response.body()?.string().orEmpty()
        if (body.contains("Fails", ignoreCase = true)) {
            throw QBAuthException("Invalid username or password")
        }
        loggedIn = true
        Log.d(TAG, "Logged in to ${cfg.displayHost()}")
    }

    private fun mapLoginHttpException(e: retrofit2.HttpException): Exception = when (e.code()) {
        403 -> QBAuthException(
            e.message()?.let { m ->
                if (m.contains("ban", true)) "Login banned: too many failed attempts, try again later"
                else "Login rejected by server (403)"
            } ?: "Login rejected by server (403)"
        )
        404 -> QBConnectException("Web API not found at this address (is qBittorrent running?)")
        else -> QBConnectException("Login failed: HTTP ${e.code()} ${e.message() ?: ""}")
    }

    companion object {
        private const val TAG = "QBApiClient"
    }
}

/** Sends the Referer header expected by qBittorrent's CSRF protection. */
private class RefererInterceptor(private val cfg: ServerConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Referer", cfg.baseUrl().trimEnd('/'))
            .build()
        return chain.proceed(request)
    }
}

/** Minimal in-memory cookie store, enough for the single SID cookie. */
class InMemoryCookieJar : CookieJar {
    private val store = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(store) {
            store.removeAll { old -> cookies.any { it.name == old.name && it.domain == old.domain } }
            store.addAll(cookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        synchronized(store) { store.filter { it.matches(url) } }

    fun clear() = synchronized(store) { store.clear() }
}

/** Trust-all HTTPS (opt-in in settings, for self-signed certificates). */
private fun applyTrustAll(builder: OkHttpClient.Builder) {
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), SecureRandom())
    }
    builder.sslSocketFactory(sslContext.socketFactory, trustManager)
    builder.hostnameVerifier { _, _ -> true }
}
