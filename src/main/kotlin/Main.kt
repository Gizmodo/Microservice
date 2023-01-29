import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import model.DisplayEvent
import mu.KotlinLogging
import rabbit.DirectExchange
import utils.Constants.barcodeName
import utils.Constants.displayName
import java.io.IOException

private val logger = KotlinLogging.logger {}
lateinit var comPortDisplay: SerialPort
lateinit var barcodePort: SerialPort
lateinit var lpos: LPOS
val comPortsList = mutableListOf<SerialPort>()
val _events = MutableSharedFlow<DisplayEvent>()
val events = _events.asSharedFlow()
lateinit var dir: DirectExchange
fun printComPorts() {
    logger.debug { "Print serial ports" }
    val ports = SerialPort.getCommPorts()
    if (ports.isEmpty()) {
        logger.debug("No COM ports")
        return
    }
    comPortsList.clear()
    SerialPort.getCommPorts().forEach { serialPort ->
        comPortsList.add(serialPort)
        if (serialPort.descriptivePortName.contains(displayName)) {
            comPortDisplay = serialPort
        }
        if (serialPort.descriptivePortName.contains(barcodeName)) {
            barcodePort = serialPort
        }
        logger.debug(serialPort.descriptivePortName.toString() + " " + serialPort.systemPortPath)
    }
}

fun disconnectComPort() {
    if (::comPortDisplay.isInitialized) {
        comPortDisplay.let {
            if (it.isOpen) {
                it.removeDataListener()
                it.closePort()
            }
        }
        logger.debug("Port closed")
    }
}

fun disconnectBarcodePort() {
    if (::barcodePort.isInitialized) {
        barcodePort.let {
            if (it.isOpen) {
                it.removeDataListener()
                it.closePort()
            }
        }
        logger.debug("Barcode port closed")
    }
}

fun connectComPort() {
    if (::barcodePort.isInitialized) {
        with(barcodePort) {
            openPort()
            addDataListener(object : SerialPortDataListener {
                override fun getListeningEvents() =
                    SerialPort.LISTENING_EVENT_DATA_AVAILABLE or SerialPort.LISTENING_EVENT_PORT_DISCONNECTED

                override fun serialEvent(event: SerialPortEvent) {
                    when (event.eventType) {
                        SerialPort.LISTENING_EVENT_DATA_RECEIVED -> {
                            logger.debug("LISTENING_EVENT_DATA_RECEIVED")
                        }

                        SerialPort.LISTENING_EVENT_DATA_AVAILABLE -> {
                            val newData = ByteArray(this@with.bytesAvailable())
                            val numRead = this@with.readBytes(newData, newData.size.toLong())
                            val str = String(newData)
                            dir.publishMessage(str)
                            logger.debug { "Read $numRead bytes. Buffer $str" }
                        }

                        SerialPort.LISTENING_EVENT_PORT_DISCONNECTED -> {
                            logger.debug("LISTENING_EVENT_PORT_DISCONNECTED")
                            disconnectBarcodePort()
                        }

                        else -> {
                            logger.debug { "ELSE at barcode SerialEvent" }
                        }
                    }
                }

            })
        }
    }
    if (::comPortDisplay.isInitialized) {
        with(comPortDisplay) {
            openPort()
            lpos = LPOS(comPortDisplay)
            addDataListener(object : SerialPortDataListener {
                override fun getListeningEvents() =
                    SerialPort.LISTENING_EVENT_DATA_WRITTEN or SerialPort.LISTENING_EVENT_PORT_DISCONNECTED

                override fun serialEvent(event: SerialPortEvent) {
                    when (event.eventType) {
                        SerialPort.LISTENING_EVENT_DATA_AVAILABLE -> {
                            logger.debug("All bytes were successfully transmitted!")
                        }

                        SerialPort.LISTENING_EVENT_PORT_DISCONNECTED -> {
                            logger.debug("LISTENING_EVENT_PORT_DISCONNECTED")
                            disconnectComPort()
                        }

                        else -> {
                        }
                    }
                }
            })
        }
    }
}

fun main() {
    workWithCoroutines()
    printComPorts()
    connectComPort()

    dir = DirectExchange().apply {
        declareExchange()
        declareQueues()
        declareBindings()
    }

    //Threads created to publish-subscribe asynchronously
    val subscribe: Thread = object : Thread() {
        override fun run() {
            try {
                dir.subscribeMessage()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
    subscribe.start()
}

fun workWithCoroutines() {
    CoroutineScope(Dispatchers.IO).launch {
        events.collect {
            when (it) {
                DisplayEvent.ClearDisplay -> {
                    logger.info { "Поступила команда на очистку дисплея" }
                    lpos.clearDisplay()
                }

                is DisplayEvent.WriteLine -> {
                    logger.info { "Поступила команда на вывод строки" }
                    lpos.writeLine(it.displayLine, it.message)
                }

                is DisplayEvent.ChangeCursor -> {
                    logger.info { "Поступила команда изменение курсора" }
                    lpos.changeCursor(it.displayCursorMode)
                }

                DisplayEvent.ScrollHorizontal -> {
                    logger.info { "Поступила команда на горизонтальную прокрутку" }
                    lpos.scrollHorizontal()
                }

                DisplayEvent.ScrollVertical -> {
                    logger.info { "Поступила команда на вертикальную прокрутку" }
                    lpos.scrollVertical()
                }

                DisplayEvent.ScrollOverwrite -> {
                    logger.info { "Поступила команда на прокрутку с перезаписью" }
                    lpos.scrollOverwrite()
                }

                is DisplayEvent.MoveTo -> {
                    logger.info { "Поступила команда на перемещение курсора" }
                    lpos.moveTo(it.direction)
                }

                is DisplayEvent.MoveToPosition -> {
                    logger.info { "Поступила команда на перемещение курсора в позицию [x;y]" }
                    lpos.moveToPosition(it.x, it.y)
                }

                DisplayEvent.DisplayInit -> {
                    logger.info { "Поступила команда на инициализацию дисплея" }
                    lpos.displayInit()
                }

                DisplayEvent.ClearLine -> {
                    logger.info { "Поступила команда на очистку линии" }
                    lpos.clearLine()
                }
            }
        }
    }
}
