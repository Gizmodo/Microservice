import com.fazecast.jSerialComm.SerialPort
import enums.CursorMode
import enums.Direction
import enums.Line

class Commands(private val port: SerialPort) {
    private val b: ByteArray = byteArrayOf(0x0C)
    fun ClearDisplay() {
        port.writeBytes(b, b.size.toLong())
    }

    fun ChangeCursor(cursorMode: CursorMode) {
        val command = byteArrayOf(0x1B, 0x5F, cursorMode.mode)
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

    fun WriteLine(line: Line, string: String) {
        var command = byteArrayOf(0x1B, 0x51, line.line)
        command += string.toByteArray() + 0x0D
        port.writeBytes(command, command.size.toLong())
    }

}