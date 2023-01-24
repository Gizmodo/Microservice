package model

import kotlinx.serialization.Serializable

@Serializable
data class Data(val a: Int, val b: String)

@Serializable
data class DisplayPayload(
    val action: String,
    val params: String
)