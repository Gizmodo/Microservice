package rabbit

import _events
import com.rabbitmq.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import model.DisplayEvent
import utils.Constants.RABBITMQ_BARCODE_QUEUE
import utils.Constants.RABBITMQ_BARCODE_ROUTING_KEY
import utils.Constants.RABBITMQ_DISPLAY_EXCHANGE
import utils.Constants.RABBITMQ_DISPLAY_QUEUE
import utils.Constants.RABBITMQ_DISPLAY_ROUTING_KEY
import utils.logger
import java.io.IOException
import java.util.concurrent.TimeoutException

class DirectExchange {
    companion object {
        val logger by logger()
    }

    @Throws(IOException::class, TimeoutException::class)
    fun declareExchange() {
        val connection = ConnectionManager.connection
        connection?.let {
            //Declare DIRECT exchange
            val channel = it.createChannel()
            channel.exchangeDeclare(
                RABBITMQ_DISPLAY_EXCHANGE,
                BuiltinExchangeType.DIRECT,
                true
            )
            channel.close()
        }
    }

    @Throws(IOException::class, TimeoutException::class)
    fun declareQueues() {
        //Create a channel - do not share the Channel instance
        val connection = ConnectionManager.connection
        connection?.let {
            val channel = it.createChannel()
            //Create the Queues
            with(channel) {
                queueDeclare(
                    RABBITMQ_DISPLAY_QUEUE,
                    true,
                    false,
                    false,
                    null
                )
                queueDeclare(
                    RABBITMQ_BARCODE_QUEUE,
                    true,
                    false,
                    false,
                    null
                )
                close()
            }
        }
    }

    //Step-3: Create the Bindings
    @Throws(IOException::class, TimeoutException::class)
    fun declareBindings() {
        ConnectionManager.connection?.let {
            val channel = it.createChannel()
            channel.queueBind(RABBITMQ_DISPLAY_QUEUE, RABBITMQ_DISPLAY_EXCHANGE, RABBITMQ_DISPLAY_ROUTING_KEY)
            channel.queueBind(RABBITMQ_BARCODE_QUEUE, RABBITMQ_DISPLAY_EXCHANGE, RABBITMQ_BARCODE_ROUTING_KEY)
            channel.close()
        }
    }

    //Step-4: Create the Subscribers
    @Throws(IOException::class)
    fun subscribeMessage() {
        val connection = ConnectionManager.connection
        connection?.let {
            val channel = it.createChannel()
            channel.basicConsume(RABBITMQ_DISPLAY_QUEUE, true, { consumerTag, message ->
                val incomingMessage = message.body.decodeToString()
                logger.info { "Тэг: $consumerTag" }
                logger.info { "Очередь: ${RABBITMQ_DISPLAY_QUEUE}\n Сообщение: $incomingMessage" }
                decodePayload(incomingMessage)
            }) { consumerTag ->
                logger.warn { consumerTag }
            }
        }
    }

    private fun decodePayload(incomingMessage: String) {
        try {
            logger.info { "Входящий JSON: $incomingMessage" }
            val displayEvent = Json.decodeFromString<DisplayEvent>(incomingMessage)
            CoroutineScope(Dispatchers.IO).launch {
                _events.emit(displayEvent)
            }
        } catch (se: SerializationException) {
            logger.error { se }
        } catch (iae: IllegalArgumentException) {
            logger.error { iae }
        } catch (e: Exception) {
            logger.error { e }
        }
    }

    //Step-5: Publish the messages
    @Throws(IOException::class, TimeoutException::class)
    fun publishMessage(message: String) {
        val connection = ConnectionManager.connection
        if (connection != null) {
            val channel = connection.createChannel()
            channel.basicPublish(
                RABBITMQ_DISPLAY_EXCHANGE,
                RABBITMQ_BARCODE_ROUTING_KEY,
                null,
                message.toByteArray()
            )
            channel.close()
        }
    }
}