import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import enums.DisplayCursorMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import model.DisplayEvent
import mu.KotlinLogging
import rabbit.DirectExchange
import utils.Constants
import java.io.IOException

private val logger = KotlinLogging.logger {}
lateinit var comPortDisplay: SerialPort
lateinit var lpos: LPOS
val comPortsList = mutableListOf<SerialPort>()
val _events = MutableSharedFlow<DisplayEvent>()
val events = _events.asSharedFlow()
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
        if (serialPort.descriptivePortName.contains(Constants.displayName)) {
            comPortDisplay = serialPort
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

fun connectComPort() {
    // disconnectComPort()
    // comPortDisplay = SerialPort.getCommPorts()[0]
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

fun main() {
    workWithCoroutines()
    printComPorts()
    connectComPort()
    lpos.clearDisplay()
    lpos.changeCursor(DisplayCursorMode.Blink)

    val dir = DirectExchange()
    dir.declareExchange()
    dir.declareQueues()
    dir.declareBindings()

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

    // Publisher
    /* val publish: Thread = object : Thread() {
         override fun run() {
             try {
                 dir.publishMessage()
             } catch (e: IOException) {
                 e.printStackTrace()
             } catch (e: TimeoutException) {
                 e.printStackTrace()
             }
         }
     }*/

    subscribe.start()
    // publish.start()
}

fun workWithCoroutines() {
    CoroutineScope(Dispatchers.IO).launch {
        events.collect {
            when (it) {
                DisplayEvent.ClearDisplay -> {
                    DirectExchange.logger.info { "Поступила команда на очистку дисплея" }
                    lpos.clearDisplay()
                }

                is DisplayEvent.WriteLine -> {
                    DirectExchange.logger.info { "Поступила команда на вывод строки" }
                    lpos.writeLine(it.displayLine, it.message)
                }

                is DisplayEvent.ChangeCursor -> {
                    DirectExchange.logger.info { "Поступила команда изменение курсора" }
                    lpos.changeCursor(it.displayCursorMode)
                }

                DisplayEvent.ScrollHorizontal -> {
                    DirectExchange.logger.info { "Поступила команда на горизонтальную прокрутку" }
                    lpos.scrollHorizontal()
                }

                DisplayEvent.ScrollVertical -> {
                    DirectExchange.logger.info { "Поступила команда на вертикальную прокрутку" }
                    lpos.scrollVertical()
                }

                DisplayEvent.ScrollOverwrite -> {
                    DirectExchange.logger.info { "Поступила команда на прокрутку с перезаписью" }
                    lpos.scrollOverwrite()
                }

                is DisplayEvent.MoveTo -> {
                    DirectExchange.logger.info { "Поступила команда на перемещение курсора" }
                    lpos.moveTo(it.direction)
                }

                is DisplayEvent.MoveToPosition -> {
                    DirectExchange.logger.info { "Поступила команда на перемещение курсора в позицию [x;y]" }
                    lpos.moveToPosition(it.x, it.y)
                }

                DisplayEvent.DisplayInit -> {
                    DirectExchange.logger.info { "Поступила команда на инициализацию дисплея" }
                    lpos.displayInit()
                }

                DisplayEvent.ClearLine -> {
                    DirectExchange.logger.info { "Поступила команда на очистку линии" }
                    lpos.clearLine()
                }
            }
        }
    }
}
