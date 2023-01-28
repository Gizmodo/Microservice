package model

import enums.Direction
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

    @Serializable
    @SerialName("ScrollVertical")
    object ScrollVertical : DisplayEvent()

    @Serializable
    @SerialName("ScrollOverwrite")
    object ScrollOverwrite : DisplayEvent()

    @Serializable
    @SerialName("MoveTo")
    data class MoveTo(val direction: Direction) : DisplayEvent()

    @Serializable
    @SerialName("MoveToPosition")
    data class MoveToPosition(val x: Byte, val y: Byte) : DisplayEvent()

    @Serializable
    @SerialName("DisplayInit")
    object DisplayInit : DisplayEvent()

}
