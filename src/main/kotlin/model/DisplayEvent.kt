package model

import enums.DisplayCursorMode
import enums.DisplayLine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class DisplayEvent {
    @Serializable
    @SerialName("ClearDisplay")
    object ClearDisplay : DisplayEvent()

    @Serializable
    @SerialName("WriteLine")
    data class WriteLine(val displayLine: DisplayLine, val message: String) : DisplayEvent()

    @Serializable
    @SerialName("ChangeCursor")
    data class ChangeCursor(val displayCursorMode: DisplayCursorMode) : DisplayEvent()

    @Serializable
    @SerialName("ScrollHorizontal")
    object ScrollHorizontal : DisplayEvent()
}
