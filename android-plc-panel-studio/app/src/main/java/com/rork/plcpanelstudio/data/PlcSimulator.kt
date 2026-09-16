package com.rork.plcpanelstudio.data

import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Built-in demo PLC so panels can be exercised without a bridge server on the LAN.
 * Inputs hold whatever the panel wrote; outputs are derived from simple ladder-style
 * logic (a start/stop seal-in circuit) plus animated analogue values.
 */
class PlcSimulator {

    private val inputs = mutableMapOf<String, Int>()
    private val outputs = mutableMapOf<String, Int>()
    private val startedAt = System.currentTimeMillis()

    @Volatile
    private var motorSealed = false

    suspend fun health(device: PlcDevice): BridgeResult<HealthResponse> {
        delay(SIM_LATENCY_MS)
        val upMinutes = (System.currentTimeMillis() - startedAt) / 60_000L
        return BridgeResult.Success(
            HealthResponse(
                ok = true,
                cpu = 6 + (elapsedSeconds() % 9).toInt(),
                ioOk = 128,
                ioTotal = 128,
                uptime = "${upMinutes / 60}h ${upMinutes % 60}m"
            ),
            latencyMs = 6 + Random.nextInt(8)
        )
    }

    suspend fun read(device: PlcDevice, addresses: List<String>): BridgeResult<Map<String, Int>> {
        delay(SIM_LATENCY_MS)
        recomputeLogic()
        val values = addresses.associateWith { address ->
            inputs[address] ?: outputs[address] ?: derive(address)
        }
        return BridgeResult.Success(values, latencyMs = 6 + Random.nextInt(8))
    }

    suspend fun write(device: PlcDevice, address: String, value: Int): BridgeResult<Int> {
        delay(SIM_LATENCY_MS)
        inputs[address] = value
        recomputeLogic()
        return BridgeResult.Success(value, latencyMs = 5 + Random.nextInt(6))
    }

    /** Seal-in: any pressed input latches the motor, any "stop"-ish input drops it. */
    private fun recomputeLogic() {
        val stopPressed = inputs.entries.any { it.key.isStopLike() && it.value > 0 }
        val startPressed = inputs.entries.any { !it.key.isStopLike() && it.value > 0 }
        if (stopPressed) motorSealed = false else if (startPressed) motorSealed = true
        val running = if (motorSealed) 1 else 0
        outputs["__motor"] = running
    }

    private fun derive(address: String): Int {
        val running = outputs["__motor"] ?: 0
        val seed = abs(address.hashCode())
        return when {
            // Analogue-looking addresses animate while the machine runs.
            address.startsWith("MW", ignoreCase = true) ||
                address.startsWith("AW", ignoreCase = true) ||
                address.startsWith("QW", ignoreCase = true) -> {
                if (running == 0) 0
                else (50 + 45 * sin(elapsedSeconds() / 3.0 + seed % 7)).toInt().coerceIn(0, 100)
            }
            // Fault-style addresses stay clear unless the operator forces them.
            address.contains("fault", ignoreCase = true) -> 0
            else -> running
        }
    }

    private fun String.isStopLike(): Boolean =
        contains("stop", ignoreCase = true) || endsWith(".4")

    private fun elapsedSeconds(): Long = (System.currentTimeMillis() - startedAt) / 1000L

    private companion object {
        const val SIM_LATENCY_MS = 90L
    }
}
