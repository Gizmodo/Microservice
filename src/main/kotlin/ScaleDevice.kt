import com.fazecast.jSerialComm.SerialPort

class ScaleDevice(private val port: SerialPort) {
    fun getInfo(){
        val command = byteArrayOf(0x02,0x01, 0xFC.toByte(), 0xFD.toByte())
        port.writeBytes(command,command.size.toLong())
    }
    fun getWeight(){
        val command = byteArrayOf(0x02,0x05,0x3A,0x30,0x30,0x33,0x30,0x3C)
        port.writeBytes(command,command.size.toLong())
    }
}