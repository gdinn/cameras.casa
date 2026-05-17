package com.gdisys.cameras.core.storage

import kotlinx.serialization.Serializable

@Serializable
data class VpnConfigTokens(
    val iPrk: String? = null,
    val iAddr: String? = null,
    val pPsk: String? = null,
)
