import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import config.ConfigUtils.loadConfig
import config.data.Config
import enums.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import model.DisplayEvent
import mu.KotlinLogging
import rabbit.DirectExchange
import utils.Constants.barcodeName
import utils.Constants.barcodeName2
import utils.Constants.displayName
import java.io.IOException
import kotlin.math.pow
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}
lateinit var comPortDisplay: SerialPort
lateinit var barcodePort: SerialPort
lateinit var scalePort: SerialPort
lateinit var lpos: LPOS
lateinit var scaleDevice: ScaleDevice
lateinit var monitorJob: Job

lateinit var config: Config

val comPortsList = mutableListOf<SerialPort>()
val _events = MutableSharedFlow<DisplayEvent>()
val events = _events.asSharedFlow()
val scaleFlow = MutableSharedFlow<String>()
lateinit var dir: DirectExchange
val shared = MutableSharedFlow<String>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
val state: Flow<String> = shared.distinctUntilChanged()
val completableJob = Job()
val coroutineScope = CoroutineScope(Dispatchers.IO + completableJob)

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
        if (serialPort.descriptivePortName.contains(barcodeName2)) {
            scalePort = serialPort
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
        logger.debug("Display com port ${config.devicesPort.display} closed")
    }
}

fun disconnectScalePort() {
    if (::scalePort.isInitialized) {
        scalePort.let {
            if (it.isOpen) {
                it.removeDataListener()
                it.closePort()
            }
        }
        monitorJob = monitorWorker()
        logger.debug("Scale port closed")
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
        monitorJob = monitorWorker()
        logger.debug("Barcode port closed")
    }
}

fun connectBarcodeComPort() {
    if (::barcodePort.isInitialized) {
        with(barcodePort) {
            openPort()
            if (::monitorJob.isInitialized) {
                monitorJob?.cancel()
            }
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
}

fun connectLPOSComPort() {
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
    loadConfig().fold(
        { error ->
            logger.error { error }
            exitProcess(404)
        },
        {
            logger.info { "Config successfully loaded" }
            config = it
            logger.info { "Config file $it" }
            dir = DirectExchange(it)
            with(dir) {
                declareExchange()
                declareQueues()
                declareBindings()
            }
            logger.info { "RabbitMQ successfully configured" }
        }
    )

    workWithCoroutines()
    printComPorts()
    connectBarcodeComPort()
    connectLPOSComPort()
    connectScalePort()
    // запуск цикла опроса весового модуля
    requestScale()

    subscribeToRabbitMQMessages()
}

fun subscribeToRabbitMQMessages() {
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

fun connectScalePort() {
    if (::scalePort.isInitialized) {
        with(scalePort) {
            openPort()
            scaleDevice = ScaleDevice(scalePort)
            if (::monitorJob.isInitialized) {
                monitorJob?.cancel()
            }
            addDataListener(object : SerialPortDataListener {
                override fun getListeningEvents() =
                    SerialPort.LISTENING_EVENT_DATA_WRITTEN or
                        SerialPort.LISTENING_EVENT_DATA_AVAILABLE or SerialPort.LISTENING_EVENT_PORT_DISCONNECTED

                override fun serialEvent(event: SerialPortEvent) {
                    when (event.eventType) {
                        SerialPort.LISTENING_EVENT_DATA_RECEIVED -> {
                            logger.debug("LISTENING_EVENT_DATA_RECEIVED")
                        }

                        SerialPort.LISTENING_EVENT_DATA_WRITTEN -> {
                            logger.info { "Data written" }
                        }

                        SerialPort.LISTENING_EVENT_DATA_AVAILABLE -> {
                            Thread.sleep(100)
                            val newData = ByteArray(this@with.bytesAvailable())
                            val numRead = this@with.readBytes(newData, newData.size.toLong())
                            parseScaleResponse(newData)

                            val str = String(newData)
                            // dir.publishMessage(str)
                            logger.debug { "Read $numRead bytes. Buffer $str" }
                        }

                        SerialPort.LISTENING_EVENT_PORT_DISCONNECTED -> {
                            logger.debug("LISTENING_EVENT_PORT_DISCONNECTED")
                            disconnectScalePort()
                        }

                        else -> {
                            logger.debug { "ELSE at ScalePort SerialEvent" }
                        }
                    }
                }


            })
        }
    }
}

private fun parseScaleResponse(data: ByteArray) {
    logger.info { data.toHexString2() }
    val length = data[2]
    when (Symbols from data[0]) {
        Symbols.ENQ -> {
            logger.info { "ENQ" }
        }

        Symbols.STX -> {
            logger.info { "STX" }
        }

        Symbols.ACK -> {
            logger.info { "ACK" }
            when (ScaleCommand from data[3]) {
                ScaleCommand.GETWEIGHT -> {
                    logger.info { "Weight response" }
                    val byteArray = byteArrayOf(data[5], data[6])
                    val shortArray = ShortArray(byteArray.size / 2) {
                        (byteArray[it * 2].toUByte().toInt() + (byteArray[(it * 2) + 1].toInt() shl 8)).toShort()
                    }
                    val state = shortArray[0].toUShort().toString(radix = 2).padStart(UByte.SIZE_BITS, '0')
                    logger.info { state }
                    if (isKthBitSet(shortArray[0].toUShort().toInt(), 0)) {
                        logger.info { "признак фиксации веса" }
                        val weightArray = byteArrayOf(data[7], data[8], data[9], data[10])
                        val littleEndianConversion = littleEndianConversion(weightArray)
                        val result = 10.toDouble().pow((-3).toDouble())
                        // logger.info { weight.toDouble()*10f.pow(-3) }
                        val roundoff = String.format("%.3f", littleEndianConversion * result)
                        //  logger.info { weight*result }
                        //  logger.info { roundoff }
                        coroutineScope.launch {
                            scaleFlow.emit(roundoff)
                            shared.tryEmit(roundoff)
                        }
                    }
                }

                else -> {
                    logger.info { "Unknown command" }
                }
            }
        }

        Symbols.NAK -> {
            logger.info { "NAK" }
        }

        Symbols.ERR -> {
            logger.info { "ERR" }
        }
    }
}

fun monitorWorker(): Job {
    return coroutineScope.launch {
        while (isActive) {
            SerialPort.getCommPorts().forEach { serialPort ->
                if (!barcodePort.isOpen) {
                    if (serialPort.descriptivePortName.contains(barcodeName)) {
                        barcodePort = serialPort
                        connectBarcodeComPort()
                    }
                }
            }
            logger.info("Попытка восстановить подключение к $barcodeName")
            delay(3000)
        }
    }
}

fun requestScale(): Job {
    return coroutineScope.launch {
        while (scalePort.isOpen) {
            scaleDevice.getWeight()
            delay(1000L)
        }
    }
}

fun workWithCoroutines() {
    CoroutineScope(Dispatchers.IO).launch {
        scaleFlow.collectLatest {
            logger.warn { it }
        }
    }
    CoroutineScope(Dispatchers.IO).launch {
        state.collectLatest {
            logger.error { it }
            dir.publishScaleWeight(it)
        }
    }
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
