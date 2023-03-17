import ch.qos.logback.classic.ClassicConstants
import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import config.ConfigUtils.getConfig
import config.data.Config
import enums.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import model.DisplayEvent
import mu.KLogger
import mu.KotlinLogging
import rabbit.DirectExchange
import java.io.IOException
import kotlin.math.pow
import kotlin.system.exitProcess

lateinit var logger: KLogger

lateinit var comPortDisplay: SerialPort
lateinit var barcodePort: SerialPort
lateinit var scalePort: SerialPort
lateinit var cubicPort: SerialPort
lateinit var lpos: LPOS
lateinit var scaleDevice: ScaleDevice
lateinit var monitorJob: Job

lateinit var config: Config

val comPortsList = mutableListOf<SerialPort>()
val _events = MutableSharedFlow<DisplayEvent>()
val events = _events.asSharedFlow()
val scaleFlow = MutableSharedFlow<String>()
lateinit var dir: DirectExchange
val weightStateResponseFlow = MutableSharedFlow<WeightChannelState>()
val shared = MutableSharedFlow<String>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
val handler = CoroutineExceptionHandler { context, exception ->
    logger.error { "Caught $exception" }
}
val state: Flow<String> = shared.distinctUntilChanged()
val completableJob = Job()
val coroutineScope = CoroutineScope(Dispatchers.IO + completableJob + handler)

fun printAvailablePorts() {
    logger.debug { "Print serial ports" }
    val ports = SerialPort.getCommPorts()
    if (ports.isEmpty()) {
        logger.debug("No COM ports")
        return
    }
    comPortsList.clear()
    SerialPort.getCommPorts().forEach { serialPort ->
        comPortsList.add(serialPort)
        if (serialPort.systemPortName.equals(config.devicesPort.display.port) &&
            config.devicesPort.display.enabled
        ) {
            comPortDisplay = serialPort
        }
        if (serialPort.systemPortName.equals(config.devicesPort.handheld.port) &&
            config.devicesPort.handheld.enabled
        ) {
            barcodePort = serialPort
        }
        if (serialPort.systemPortName.equals(config.devicesPort.cubic.port) &&
            config.devicesPort.cubic.enabled
        ) {
            cubicPort = serialPort
        }
        if (serialPort.systemPortName.equals(config.devicesPort.scales.port) &&
            config.devicesPort.scales.enabled
        ) {
            scalePort = serialPort
        }
        logger.debug(
            serialPort.descriptivePortName.toString() + " " + serialPort.systemPortName
        )
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
        logger.debug("Display com port ${config.devicesPort.display.port} closed")
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

fun disconnectCubic() {
    if (::cubicPort.isInitialized) {
        cubicPort.let {
            if (it.isOpen) {
                it.removeDataListener()
                it.closePort()
            }
        }
        monitorJob = monitorWorker()
        logger.debug("Cubic port closed")
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

fun connectCubicPort() {
    if (!config.devicesPort.cubic.enabled) return
    if (::cubicPort.isInitialized) {
        with(cubicPort) {
            openPort()
            if (::monitorJob.isInitialized) {
                monitorJob.cancel()
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
                            Thread.sleep(100)
                            val newData = ByteArray(this@with.bytesAvailable())
                            val numRead = this@with.readBytes(newData, newData.size.toLong())
                            val str = String(newData)
                            dir.publishMessage(str)
                            logger.debug { "Прочитан штрихкод с кубика $str" }
                        }

                        SerialPort.LISTENING_EVENT_PORT_DISCONNECTED -> {
                            logger.debug("LISTENING_EVENT_PORT_DISCONNECTED")
                            disconnectCubic()
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

fun connectBarcodeComPort() {
    if (!config.devicesPort.handheld.enabled) return
    if (::barcodePort.isInitialized) {
        with(barcodePort) {
            openPort()
            if (::monitorJob.isInitialized) {
                monitorJob.cancel()
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
    if (!config.devicesPort.display.enabled) return
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

fun loadConfig() {
    getConfig().fold(
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
    if (!config.devicesPort.scales.enabled) return
    if (::scalePort.isInitialized) {
        with(scalePort) {
            openPort()
            scaleDevice = ScaleDevice(scalePort)
            requestScale()
            if (::monitorJob.isInitialized) {
                monitorJob.cancel()
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
                            //  logger.info { "Data written" }
                        }

                        SerialPort.LISTENING_EVENT_DATA_AVAILABLE -> {
                            Thread.sleep(100)
                            val newData = ByteArray(this@with.bytesAvailable())
                            val numRead = this@with.readBytes(newData, newData.size.toLong())
                            onScaleResponse(newData)

                            val str = String(newData)
                            // dir.publishMessage(str)
                            // logger.debug { "Read $numRead bytes. Buffer $str" }
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

private fun onScaleResponse(data: ByteArray) {
    //  logger.info { data.toHexString2() }
    when (Symbols from data[0]) {
        Symbols.ENQ -> {
            logger.info { "ENQ" }
        }

        Symbols.STX -> {
            logger.info { "STX" }
        }

        Symbols.ACK -> {
            //logger.info { "ACK" }
            when (ScaleCommand from data[3]) {
                ScaleCommand.GETWEIGHT -> {
                 //   parseWeightResponseState(data)
                    val byteArray = byteArrayOf(data[5], data[6])

                    val shortArray = ShortArray(byteArray.size / 2) {
                        (byteArray[it * 2].toUByte().toInt() + (byteArray[(it * 2) + 1].toInt() shl 8)).toShort()
                    }
                    val state = shortArray[0].toUShort().toString(radix = 2).padStart(UByte.SIZE_BITS, '0')
                    if (isKthBitSet(shortArray[0].toUShort().toInt(), 0)) {
                        logger.info { "признак фиксации веса" }
                        val weightArray = byteArrayOf(data[7], data[8], data[9], data[10])
                        val littleEndianConversion = littleEndianConversion(weightArray)
                        val result = 10.toDouble().pow((-3).toDouble())
                        val roundOff = String.format("%.3f", littleEndianConversion * result)
                        coroutineScope.launch {
                            scaleFlow.emit(roundOff)
                            shared.tryEmit(roundOff)
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

fun parseWeightResponseState(data: ByteArray) {
    val stateField = byteArrayOf(data[5], data[6])
    val shortArray = ShortArray(stateField.size / 2) {
        (stateField[it * 2].toUByte().toInt() + (stateField[(it * 2) + 1].toInt() shl 8)).toShort()
    }
    val state = shortArray[0].toUShort().toString(radix = 2).padStart(UByte.SIZE_BITS, '0')
    val setInt = shortArray[0].toUShort().toInt()
    coroutineScope.launch {
        if (isKthBitSet(setInt, 0)) {
            val weightArray = byteArrayOf(data[7], data[8], data[9], data[10])
            val littleEndianConversion = littleEndianConversion(weightArray)
            val result = 10.toDouble().pow((-3).toDouble())
            val roundOff = String.format("%.3f", littleEndianConversion * result)
            weightStateResponseFlow.emit(WeightChannelState.Fixed(roundOff))
        }
        if (isKthBitSet(setInt, 1)) {
            weightStateResponseFlow.emit(WeightChannelState.AutoNull)
        }
        if (!isKthBitSet(setInt, 2)) {
            weightStateResponseFlow.emit(WeightChannelState.ChannelDown)
        }
        if (isKthBitSet(setInt, 3)) {
            weightStateResponseFlow.emit(WeightChannelState.Tare)
        }
        if (isKthBitSet(setInt, 4)) {
            weightStateResponseFlow.emit(WeightChannelState.StableWeight)
        }
        if (isKthBitSet(setInt, 5)) {
            weightStateResponseFlow.emit(WeightChannelState.AutoNullOnPowerOn)
        }
        if (isKthBitSet(setInt, 6)) {
            weightStateResponseFlow.emit(WeightChannelState.Overload)
        }
        if (isKthBitSet(setInt, 7)) {
            weightStateResponseFlow.emit(WeightChannelState.ErrorMeasurement)
        }
        if (isKthBitSet(setInt, 8)) {
            weightStateResponseFlow.emit(WeightChannelState.Underload)
        }
        if (isKthBitSet(setInt, 9)) {
            weightStateResponseFlow.emit(WeightChannelState.ErrorADC)
        }
    }
}

fun monitorWorker(): Job {
    return coroutineScope.launch {
        while (isActive) {
            SerialPort.getCommPorts().forEach { serialPort ->
                if (!barcodePort.isOpen) {
                    logger.info("Попытка восстановить подключение к ручному сканеру ${config.devicesPort.handheld.port}")
                    if (serialPort.systemPortName.equals(config.devicesPort.handheld.port)) {
                        barcodePort = serialPort
                        connectBarcodeComPort()
                    }
                }
                if (!cubicPort.isOpen) {
                    logger.info("Попытка восстановить подключение к кубику ${config.devicesPort.cubic.port}")
                    if (serialPort.systemPortName.equals(config.devicesPort.cubic.port)) {
                        cubicPort = serialPort
                        connectCubicPort()
                    }
                }
            }
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

fun startCollectFlowStates() {
    CoroutineScope(Dispatchers.IO).launch {
        scaleFlow.collectLatest {
            logger.debug {"scaleFlow $it" }
        }
    }
    CoroutineScope(Dispatchers.IO).launch {
        state.collectLatest {
            logger.debug {"state $it" }
            dir.publishScaleWeight(it)
        }
    }
    CoroutineScope(Dispatchers.IO).launch {
        weightStateResponseFlow.collectLatest {
            when (it) {
                is WeightChannelState.Fixed -> {
                    logger.info { "признак фиксации веса" }
                    logger.error { "!!!!!!!!!!!!!!! -> " + it.weight }
                    dir.publishScaleWeight(it.weight)
                }

                WeightChannelState.AutoNull -> {
                    logger.info { "работа автонуля" }
                }

                WeightChannelState.AutoNullOnPowerOn -> {
                    logger.error { "ошибка автонуля при включении" }
                }

                WeightChannelState.ChannelDown -> {
                    logger.error { "канал выключен" }
                }

                WeightChannelState.ErrorADC -> {
                    logger.error { "нет ответа от АЦП" }
                }

                WeightChannelState.ErrorMeasurement -> {
                    logger.error { "ошибка при получении измерения" }
                }

                WeightChannelState.Overload -> {
                    logger.error { "перегрузка по весу" }
                }

                WeightChannelState.Reserved -> {}
                WeightChannelState.Tare -> {}
                WeightChannelState.StableWeight -> {
                    logger.info { "признак успокоения веса" }
                }

                WeightChannelState.Underload -> {
                    logger.info { "весы недогружены" }
                }
            }
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

fun main() {
    System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback.xml");
    logger = KotlinLogging.logger {}

    loadConfig()
    startCollectFlowStates()
    printAvailablePorts()
    connectDevices()
    // запуск цикла опроса весового модуля
    subscribeToRabbitMQMessages()
}

fun connectDevices() {
    connectBarcodeComPort()
    connectLPOSComPort()
    connectScalePort()
    connectCubicPort()
}