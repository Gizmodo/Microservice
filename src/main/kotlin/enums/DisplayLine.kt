package enums

import kotlinx.serialization.Serializable

@Serializable
enum class DisplayLine(val displayLine: Byte) {
    First(0x41),
    Second(0x42),
    FirstScroll(0x44)
}