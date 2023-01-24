import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import enums.CursorMode
import enums.Line
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import mu.KotlinLogging
import rabbit.DirectExchange
import utils.Events
import java.io.IOException

private val logger = KotlinLogging.logger {}
lateinit var comPortDisplay: SerialPort
lateinit var commands: Commands
val comPortsList = mutableListOf<SerialPort>()
val _events = MutableSharedFlow<Events>()
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
        commands = Commands(comPortDisplay)
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

fun writeComPort() {
    val s: String = "32r3"
    if (::comPortDisplay.isInitialized && comPortDisplay.isOpen) {
//        comPort.writeBytes(s.toByteArray(), s.length.toLong())
        commands.ClearDisplay()
    }
}

fun main() {
    workWithCoroutines()
    printComPorts()
    connectComPort()
    writeComPort()
    commands.ChangeCursor(CursorMode.Off)
    commands.WriteLine(Line.Second, "IRA & MArUSYA")
    /*   commands.ChangeCursor(CursorMode.Blink)
       Thread.sleep(2000)
       commands.ChangeCursor(CursorMode.Off)
       Thread.sleep(2000)
       commands.ChangeCursor(CursorMode.Filled)
       Thread.sleep(2000)*/
    val dir = DirectExchange()
    dir.declareExchange()
    dir.declareQueues()
    dir.declareBindings()

    //Threads created to publish-subscribe asynchronously

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
                Events.ClearDisplay -> {
                    logger.debug("ClearDisplay")
                }

                is Events.ClearLine -> {
                    logger.debug("ClearLine")
                }

                is Events.ShowMessage -> {
                    logger.debug("ShowMessage")
                }
            }
        }
    }
}