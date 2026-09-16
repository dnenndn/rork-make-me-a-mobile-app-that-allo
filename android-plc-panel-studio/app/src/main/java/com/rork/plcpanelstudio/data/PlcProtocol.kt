package com.rork.plcpanelstudio.data

import kotlinx.serialization.Serializable

/**
 * Wire format spoken with the LAN bridge server that sits next to the PLCs.
 *
 *  GET  http://host:port/api/health           -> [HealthResponse]
 *  POST http://host:port/api/read   ReadRequest  -> [ReadResponse]
 *  POST http://host:port/api/write  WriteRequest -> [WriteResponse]
 */
@Serializable
data class HealthResponse(
    val ok: Boolean = true,
    val cpu: Int? = null,
    val ioOk: Int? = null,
    val ioTotal: Int? = null,
    val uptime: String? = null
)

@Serializable
data class ReadRequest(val addresses: List<String>)

@Serializable
data class ReadResponse(val values: Map<String, Int> = emptyMap())

@Serializable
data class WriteRequest(val address: String, val value: Int)

@Serializable
data class WriteResponse(val ok: Boolean = true, val value: Int? = null)
