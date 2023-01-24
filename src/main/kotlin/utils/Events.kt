package test.utils

import test.enums.Line

sealed class Events {
    data class ShowMessage(val message: String) : Events()
    object ClearDisplay : Events()
    data class ClearLine(val line: Line) : Events()
}
