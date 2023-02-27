package config.data

data class RabbitMQ(
    val exchange: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val queues: RabbitQueues,
    val routingKeys: RabbitRoutingKeys,
    val timeouts: RabbitTimeouts
)
