package test.rabbit

import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Recoverable
import com.rabbitmq.client.RecoveryListener
import test.Constants.RABBITMQ_CONNECTION_TIMEOUT
import test.Constants.RABBITMQ_HEART_BEAT_TIMEOUT
import test.Constants.RABBITMQ_NETWORK_RECOVER_INTERVAL
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object ConnectionManager {
    var connection: Connection? = null
        /**
         * Create RabbitMQ Connection
         *
         * @return Connection
         */
        get() {
            if (field == null) {
                try {
                    val connectionFactory = ConnectionFactory().apply {
                        host = "192.168.88.22"
                        port = 5672
                        username = "admin"
                        password = "1"
                        isAutomaticRecoveryEnabled = true
                        networkRecoveryInterval = TimeUnit.SECONDS.toMillis(RABBITMQ_NETWORK_RECOVER_INTERVAL)
                        requestedHeartbeat = RABBITMQ_HEART_BEAT_TIMEOUT
                        connectionTimeout = TimeUnit.SECONDS.toMillis(RABBITMQ_CONNECTION_TIMEOUT).toInt()
                    }
                    val connection = connectionFactory.newConnection("ConnectionName")
                    (connection as Recoverable).addRecoveryListener(object : RecoveryListener {
                        override fun handleRecovery(recoverable: Recoverable?) {
                            println("rabbitmq connection is recovering on ${connection.address}")
                        }

                        override fun handleRecoveryStarted(recoverable: Recoverable?) {
                            println("start to recover rabbitmq connection on ${connection.address}")
                        }
                    })
                    field = connection
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: TimeoutException) {
                    e.printStackTrace()
                }
            }
            return field
        }
        private set
}