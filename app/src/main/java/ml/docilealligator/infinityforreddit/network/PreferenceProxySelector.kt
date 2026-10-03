package ml.docilealligator.infinityforreddit.network

import android.content.SharedPreferences
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.Collections
import java.util.WeakHashMap
import kotlin.concurrent.thread
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

/**
 * Settings > Proxy, read each time OkHttp opens a connection rather than once when a client is built.
 *
 * The OkHttp clients are app-wide singletons, so a proxy handed to `OkHttpClient.Builder.proxy` stayed
 * whatever it was at launch until the process died. OkHttp asks a selector for every new connection.
 * What a selector cannot reach is a connection already pooled, so a change also empties every pool
 * registered through [track].
 *
 * With the proxy off it defers to the system selector, which is what a client without a proxy of its
 * own used before: a proxy set for the Wi-Fi network still applies.
 */
class PreferenceProxySelector private constructor(
    private val proxySharedPreferences: SharedPreferences,
) : ProxySelector() {

    private val systemProxySelector: ProxySelector? = getDefault()

    private val connectionPools: MutableSet<ConnectionPool> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    /** Held in a field: SharedPreferences keeps only a weak reference to its listeners. */
    private val proxyPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> evictPooledConnections() }

    init {
        proxySharedPreferences.registerOnSharedPreferenceChangeListener(proxyPreferenceListener)
    }

    override fun select(uri: URI?): List<Proxy> {
        val proxy = configuredProxy()
        if (proxy != null) {
            return listOf(proxy)
        }
        return systemProxySelector?.select(uri)?.takeIf { it.isNotEmpty() } ?: listOf(Proxy.NO_PROXY)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        if (configuredProxy() == null) {
            systemProxySelector?.connectFailed(uri, sa, ioe)
        }
    }

    /** Has [client]'s connections dropped when the proxy changes. Returns [client]. */
    fun track(client: OkHttpClient): OkHttpClient {
        track(client.connectionPool)
        return client
    }

    /** Has [pool]'s connections dropped when the proxy changes. Returns [pool]. */
    fun track(pool: ConnectionPool): ConnectionPool {
        connectionPools.add(pool)
        return pool
    }

    /** The proxy Settings > Proxy names, or null when it names none. */
    private fun configuredProxy(): Proxy? {
        if (!proxySharedPreferences.getBoolean(SharedPreferencesUtils.PROXY_ENABLED, false)) {
            return null
        }
        val proxyType = Proxy.Type.valueOf(
            proxySharedPreferences.getString(SharedPreferencesUtils.PROXY_TYPE, "HTTP") ?: "HTTP"
        )
        if (proxyType == Proxy.Type.DIRECT) {
            return null
        }
        val proxyHost = proxySharedPreferences.getString(SharedPreferencesUtils.PROXY_HOSTNAME, "127.0.0.1")
            ?: "127.0.0.1"
        val proxyPort = SharedPreferencesUtils.getInt(
            proxySharedPreferences, SharedPreferencesUtils.PROXY_PORT, "1080"
        )
        return Proxy(proxyType, InetSocketAddress.createUnresolved(proxyHost, proxyPort))
    }

    /**
     * Off the main thread, which is where the listener runs: closing a TLS connection writes to the
     * socket. A connection busy at that moment is out of reach and goes back to its pool afterwards;
     * with the user on the settings screen there is seldom one.
     */
    private fun evictPooledConnections() {
        val pools = synchronized(connectionPools) { connectionPools.toList() }
        thread(name = "ProxyChangeEvictor") {
            pools.forEach { it.evictAll() }
        }
    }

    companion object {
        @Volatile
        private var instance: PreferenceProxySelector? = null

        /** The one selector for [proxySharedPreferences], the Settings > Proxy file. */
        @JvmStatic
        fun get(proxySharedPreferences: SharedPreferences): PreferenceProxySelector {
            return instance ?: synchronized(this) {
                instance ?: PreferenceProxySelector(proxySharedPreferences).also { instance = it }
            }
        }
    }
}
