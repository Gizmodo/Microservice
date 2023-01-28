import com.fazecast.jSerialComm.SerialPort
import enums.Direction
import enums.DisplayCursorMode
import enums.DisplayLine
import utils.Converter

class LPOS(private val port: SerialPort) {
    fun clearDisplay() {
        val command = byteArrayOf(0x0C)
        port.writeBytes(command, command.size.toLong())
    }

    fun changeCursor(displayCursorMode: DisplayCursorMode) {
        val command = byteArrayOf(0x1B, 0x5F, displayCursorMode.cursorMode)
        port.writeBytes(command, command.size.toLong())
    }

    fun scrollHorizontal() {
        val command = byteArrayOf(0x1B, 0x13)
        port.writeBytes(command, command.size.toLong())
    }

    fun scrollVertical() {
        val command = byteArrayOf(0x1B, 0x12)
        port.writeBytes(command, command.size.toLong())
    }

    fun scrollOverwrite() {
        val command = byteArrayOf(0x1B, 0x11)
        port.writeBytes(command, command.size.toLong())
    }

    fun moveTo(direction: Direction) {
        val command = byteArrayOf(0x1B, 0x11, direction.direction)
        port.writeBytes(command, command.size.toLong())
    }

    fun moveToPosition(x: Byte, y: Byte) {
        if (((y >= 0x01) && (y <= 0x02)) && ((x >= 0x01) && (x <= 0x14))) {
            val command = byteArrayOf(0x1B, 0x6C, x, y)
            port.writeBytes(command, command.size.toLong())
        } else {
            println("Неверная координата")
        }
    }

    fun displayInit() {
        val command = byteArrayOf(0x1B, 0x40)
        port.writeBytes(command, command.size.toLong())
    }

    fun clearLine() {
        val command = byteArrayOf(0x18)
        port.writeBytes(command, command.size.toLong())
    }

    fun writeLine(displayLine: DisplayLine, string: String) {
        var command = byteArrayOf(0x1B, 0x51, displayLine.displayLine)
        val convertedString = Converter.convertMessage(string)
        command += convertedString + 0x0D
        port.writeBytes(command, command.size.toLong())
    }
}