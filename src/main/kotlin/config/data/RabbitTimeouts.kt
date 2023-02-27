package config.data

data class RabbitTimeouts(
    val heartbeat: Int,
    val networkRecover: Long,
    val connection: Long
)
