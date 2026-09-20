package com.rork.plcpanelstudio.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

/** Result of a bridge call, carrying either data or a human-readable failure reason. */
sealed interface BridgeResult<out T> {
    /** [viaBackup] is true when the answer came from the device's backup address. */
    data class Success<T>(val data: T, val latencyMs: Int, val viaBackup: Boolean = false) : BridgeResult<T>
    data class Failure(val reason: String) : BridgeResult<Nothing>
}

/**
 * Talks to the LAN bridge server that proxies Modbus/PLC traffic.
 * Simulated devices never reach the network — [PlcSimulator] answers instead.
 *
 * A device can have a backup address (e.g. Tailscale). The main address is tried first; if it
 * does not answer, the backup is tried. Only when both fail is the device reported offline.
 * See [FailoverPlanner] for how the order is chosen.
 */
class PlcBridgeClient(
    private val simulator: PlcSimulator
) {
    private val planner = FailoverPlanner()

    private val client: HttpClient by lazy {
        HttpClient(Android) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }
    }

    suspend fun health(device: PlcDevice): BridgeResult<HealthResponse> {
        if (device.simulated) return simulator.health(device)
        return callBridge(device) { baseUrl, timeoutMs ->
            client.get("$baseUrl/api/health") { limit(timeoutMs) }.body<HealthResponse>()
        }
    }

    suspend fun read(device: PlcDevice, addresses: List<String>): BridgeResult<Map<String, Int>> {
        if (addresses.isEmpty()) return BridgeResult.Success(emptyMap(), 0)
        if (device.simulated) return simulator.read(device, addresses)
        return callBridge(device) { baseUrl, timeoutMs ->
            val response: ReadResponse = client.post("$baseUrl/api/read") {
                limit(timeoutMs)
                contentType(ContentType.Application.Json)
                setBody(ReadRequest(addresses))
            }.body()
            response.values
        }
    }

    suspend fun write(device: PlcDevice, address: String, value: Int): BridgeResult<Int> {
        if (device.simulated) return simulator.write(device, address, value)
        return callBridge(device) { baseUrl, timeoutMs ->
            val response: WriteResponse = client.post("$baseUrl/api/write") {
                limit(timeoutMs)
                contentType(ContentType.Application.Json)
                setBody(WriteRequest(address, value))
            }.body()
            response.value ?: value
        }
    }

    private fun HttpRequestBuilder.limit(timeoutMs: Long) {
        timeout {
            requestTimeoutMillis = timeoutMs
            connectTimeoutMillis = timeoutMs
            socketTimeoutMillis = timeoutMs
        }
    }

    /** Runs [call] against the device's addresses in the planner's order until one answers. */
    private suspend fun <T> callBridge(
        device: PlcDevice,
        call: suspend (baseUrl: String, timeoutMs: Long) -> T
    ): BridgeResult<T> {
        val endpoints = device.endpoints()
        val order = planner.order(device.id, hasBackup = endpoints.size > 1)
        val reasons = arrayOfNulls<String>(endpoints.size)

        for ((position, index) in order.withIndex()) {
            val endpoint = endpoints[index]
            // With another address still to try, give up on this one sooner.
            val timeoutMs = if (position == order.lastIndex) REQUEST_TIMEOUT_MS else FIRST_ATTEMPT_TIMEOUT_MS
            val started = System.currentTimeMillis()
            try {
                val data = call("http://${endpoint.host}:${endpoint.port}", timeoutMs)
                planner.record(device.id, index, success = true)
                return BridgeResult.Success(
                    data = data,
                    latencyMs = (System.currentTimeMillis() - started).toInt(),
                    viaBackup = index == 1
                )
            } catch (error: Exception) {
                // A cancelled request is not a failed address: let the cancellation through.
                currentCoroutineContext().ensureActive()
                planner.record(device.id, index, success = false)
                reasons[index] = describe(error)
            }
        }
        return BridgeResult.Failure(combineFailureReasons(reasons[0] ?: "No response", reasons.getOrNull(1)))
    }

    private fun describe(error: Exception): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("timeout", ignoreCase = true) -> "Timeout"
            message.contains("Unable to resolve host", ignoreCase = true) -> "Host not found"
            message.contains("ECONNREFUSED", ignoreCase = true) ||
                message.contains("refused", ignoreCase = true) -> "Connection refused"
            message.contains("Network is unreachable", ignoreCase = true) -> "Network unreachable"
            message.contains("No route to host", ignoreCase = true) -> "No route to host"
            message.isBlank() -> "No response"
            else -> message.take(80)
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 4_000L
        /** Used when a backup address can still be tried, so switching over is quick. */
        const val FIRST_ATTEMPT_TIMEOUT_MS = 2_000L
    }
}
