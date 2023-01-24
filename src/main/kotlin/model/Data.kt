package model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class Data(val a: Int, val b: String)

@Serializable
data class DisplayPayload(
    val action: String,
    val params: String
)

fun main1() {
    val obj = Json.decodeFromString<Data>("""{"a":42, "b": "str"}""")
}