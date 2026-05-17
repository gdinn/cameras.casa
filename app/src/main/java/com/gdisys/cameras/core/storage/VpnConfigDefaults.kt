package com.gdisys.cameras.core.storage

import kotlinx.serialization.Serializable

@Serializable
data class VpnConfigDefaults(
    val iDns: String? = null,
    val iMtu: String? = null,
    val pPuk: String? = null,
    val pAllowedips: String? = null,
    val pEndpoint: String? = null,
    val pPersistentKeepAlive: String? = null
)