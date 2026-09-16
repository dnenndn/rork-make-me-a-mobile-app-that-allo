package com.rork.plcpanelstudio.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Result of a bridge call, carrying either data or a human-readable failure reason. */
sealed interface BridgeResult<out T> {
    data class Success<T>(val data: T, val latencyMs: Int) : BridgeResult<T>
    data class Failure(val reason: String) : BridgeResult<Nothing>
}

/**
 * Talks to the LAN bridge server that proxies Modbus/PLC traffic.
 * Simulated devices never reach the network — [PlcSimulator] answers instead.
 */
class PlcBridgeClient(
    private val simulator: PlcSimulator
) {
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
        return safeCall {
            client.get("${baseUrl(device)}/api/health").body<HealthResponse>()
        }
    }

    suspend fun read(device: PlcDevice, addresses: List<String>): BridgeResult<Map<String, Int>> {
        if (addresses.isEmpty()) return BridgeResult.Success(emptyMap(), 0)
        if (device.simulated) return simulator.read(device, addresses)
        return safeCall {
            val response: ReadResponse = client.post("${baseUrl(device)}/api/read") {
                contentType(ContentType.Application.Json)
                setBody(ReadRequest(addresses))
            }.body()
            response.values
        }
    }

    suspend fun write(device: PlcDevice, address: String, value: Int): BridgeResult<Int> {
        if (device.simulated) return simulator.write(device, address, value)
        return safeCall {
            val response: WriteResponse = client.post("${baseUrl(device)}/api/write") {
                contentType(ContentType.Application.Json)
                setBody(WriteRequest(address, value))
            }.body()
            response.value ?: value
        }
    }

    private fun baseUrl(device: PlcDevice): String = "http://${device.host}:${device.port}"

    private suspend fun <T> safeCall(block: suspend () -> T): BridgeResult<T> {
        val started = System.currentTimeMillis()
        return try {
            val data = block()
            BridgeResult.Success(data, (System.currentTimeMillis() - started).toInt())
        } catch (error: Exception) {
            BridgeResult.Failure(describe(error))
        }
    }

    private fun describe(error: Exception): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("timeout", ignoreCase = true) -> "Timeout"
            message.contains("Unable to resolve host", ignoreCase = true) -> "Host not found"
            message.contains("ECONNREFUSED", ignoreCase = true) ||
                message.contains("refused", ignoreCase = true) -> "Connection refused"
            message.contains("Network is unreachable", ignoreCase = true) -> "Network unreachable"
            message.isBlank() -> "No response"
            else -> message.take(80)
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 4_000L
    }
}
