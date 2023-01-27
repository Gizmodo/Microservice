package enums

import kotlinx.serialization.Serializable

@Serializable
enum class DisplayCursorMode(val cursorMode: Byte) {
    Off(0),
    Blink(1),
    Filled(3),
}
