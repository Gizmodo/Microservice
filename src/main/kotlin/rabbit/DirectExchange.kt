package test.rabbit

import com.rabbitmq.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import test.Constants.RABBITMQ_DISPLAY_EXCHANGE
import test.Constants.RABBITMQ_DISPLAY_QUEUE
import test.Constants.RABBITMQ_DISPLAY_ROUTING_KEY
import test._events
import test.commands
import test.enums.Line
import test.model.DisplayPayload
import test.utils.Events
import java.io.IOException
import java.util.concurrent.TimeoutException


/**
 * Selective message broadcast with routingkey filter.
 */
class DirectExchange() {


    //Step-1: Declare the exchange
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
                println(consumerTag)
                println("${RABBITMQ_DISPLAY_QUEUE}: $incomingMessage")
                handlePayload(incomingMessage)
                commands.ClearDisplay()
                commands.WriteLine(Line.First, incomingMessage)
            }) { consumerTag -> println(consumerTag) }
        }
    }

    private fun handlePayload(incomingMessage: String) {
        //incomingMessage JSON string
        try {
            val obj = Json.decodeFromString<DisplayPayload>(incomingMessage)
            CoroutineScope(Dispatchers.IO).launch {
                _events.emit(Events.ClearDisplay)
            }
        } catch (e: Exception) {
            println(e.toString())
        }
    }

    //Step-5: Publish the messages
    @Throws(IOException::class, TimeoutException::class)
    fun publishMessage() {
        val connection = ConnectionManager.connection
        if (connection != null) {
            val channel = connection.createChannel()
            val message = "Direct message - Turn on the Home Appliances "
            channel.basicPublish("my-direct-exchange", "homeAppliance", null, message.toByteArray())
            channel.close()
        }
    }
}