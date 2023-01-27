import com.fazecast.jSerialComm.SerialPort
import enums.Direction
import enums.DisplayCursorMode
import enums.DisplayLine
import utils.Converter

class LPOS(private val port: SerialPort) {
    fun ClearDisplay() {
        val command = byteArrayOf(0x0C)
        port.writeBytes(command, command.size.toLong())
    }

    fun ChangeCursor(displayCursorMode: DisplayCursorMode) {
        val command = byteArrayOf(0x1B, 0x5F, displayCursorMode.cursorMode)
        port.writeBytes(command, command.size.toLong())
    }

    fun ScrollHorizontal() {
        val command = byteArrayOf(0x1B, 0x13)
        port.writeBytes(command, command.size.toLong())
    }

    fun ScrollVertical() {
        val command = byteArrayOf(0x1B, 0x12)
        port.writeBytes(command, command.size.toLong())
    }

    fun ScrollOverwrite() {
        val command = byteArrayOf(0x1B, 0x11)
        port.writeBytes(command, command.size.toLong())
    }

    fun MoveTo(direction: Direction) {
        val command = byteArrayOf(0x1B, 0x11, direction.direction)
        port.writeBytes(command, command.size.toLong())
    }

    fun MoveToPosition(x: Byte, y: Byte) {
        if (((y >= 0x01) && (y <= 0x02)) && ((x >= 0x01) && (x <= 0x14))) {
            val command = byteArrayOf(0x1B, 0x6C, x, y)
            port.writeBytes(command, command.size.toLong())
        } else {
            println("Неверная координата")
        }
    }

    fun DisplayInit() {
        val command = byteArrayOf(0x1B, 0x40)
        port.writeBytes(command, command.size.toLong())
    }

    fun ClearLine() {
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