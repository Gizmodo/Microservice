package rabbit

import _events
import com.rabbitmq.client.*
import config.data.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import model.DisplayEvent
import utils.logger
import java.io.IOException
import java.util.concurrent.TimeoutException

class DirectExchange(private val config: Config) {
    companion object {
        val logger by logger()
    }

    @Throws(IOException::class, TimeoutException::class)
    fun declareExchange() {
        val connection = ConnectionManager.instance(config).connection
        connection?.let {
            //Declare DIRECT exchange
            val channel = it.createChannel()
            channel.exchangeDeclare(
                config.rabbitmq.exchange,
                BuiltinExchangeType.DIRECT,
                true
            )
            channel.close()
        }
    }

    @Throws(IOException::class, TimeoutException::class)
    fun declareQueues() {
        //Create a channel - do not share the Channel instance
        val connection = ConnectionManager.instance(config).connection
        connection?.let {
            val channel = it.createChannel()
            //Create the Queues
            with(channel) {
                queueDeclare(
                    config.rabbitmq.queues.displayQueue,
                    true,
                    false,
                    false,
                    null
                )
                queueDeclare(
                    config.rabbitmq.queues.scannerQueue,
                    true,
                    false,
                    false,
                    null
                )
                queueDeclare(
                    config.rabbitmq.queues.scaleQueue,
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
        val connection = ConnectionManager.instance(config).connection
        connection?.let {
            val channel = it.createChannel()
            channel.queueBind(
                config.rabbitmq.queues.displayQueue,
                config.rabbitmq.exchange,
                config.rabbitmq.routingKeys.displayKey
            )
            channel.queueBind(
                config.rabbitmq.queues.scannerQueue,
                config.rabbitmq.exchange,
                config.rabbitmq.routingKeys.scannerKey
            )
            channel.queueBind(
                config.rabbitmq.queues.scaleQueue, config.rabbitmq.exchange,
                config.rabbitmq.routingKeys.scaleKey
            )
            channel.close()
        }
    }

    //Step-4: Create the Subscribers
    @Throws(IOException::class)
    fun subscribeMessage() {
        val connection = ConnectionManager.instance(config).connection
        connection?.let {
            val channel = it.createChannel()
            channel.basicConsume(config.rabbitmq.queues.displayQueue, true, { consumerTag, message ->
                val incomingMessage = message.body.decodeToString()
                logger.info { "Тэг: $consumerTag" }
                logger.info { "Очередь: ${config.rabbitmq.queues.displayQueue}\n Сообщение: $incomingMessage" }
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
        val connection = ConnectionManager.instance(config).connection
        if (connection != null) {
            val channel = connection.createChannel()
            channel.basicPublish(
                config.rabbitmq.exchange,
                config.rabbitmq.routingKeys.scannerKey,
                null,
                message.toByteArray()
            )
            channel.close()
        }
    }

    @Throws(IOException::class, TimeoutException::class)
    fun publishScaleWeight(message: String) {
        val connection = ConnectionManager.instance(config).connection
        if (connection != null) {
            val channel = connection.createChannel()
            channel.basicPublish(
                config.rabbitmq.exchange,
                config.rabbitmq.routingKeys.scaleKey,
                null,
                message.toByteArray()
            )
            channel.close()
        }
    }
}