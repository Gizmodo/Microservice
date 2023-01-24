package utils

import enums.Line

sealed class Events {
    data class ShowMessage(val message: String) : Events()
    object ClearDisplay : Events()
    data class ClearLine(val line: Line) : Events()
}
