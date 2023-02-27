package rabbit

import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Recoverable
import com.rabbitmq.client.RecoveryListener
import config.data.Config
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object ConnectionManager {
    private lateinit var config: Config
    fun instance(cfg: Config): ConnectionManager {
        config = cfg
        return this
    }

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
                        host = config.rabbitmq.host
                        port = config.rabbitmq.port
                        username = config.rabbitmq.username
                        password = config.rabbitmq.password
                        isAutomaticRecoveryEnabled = true
                        networkRecoveryInterval = TimeUnit.SECONDS.toMillis(config.rabbitmq.timeouts.networkRecover)
                        requestedHeartbeat = config.rabbitmq.timeouts.heartbeat
                        connectionTimeout = TimeUnit.SECONDS.toMillis(config.rabbitmq.timeouts.connection).toInt()
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