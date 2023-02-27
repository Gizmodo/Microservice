package config.data

data class RabbitQueues(
    val scannerQueue: String,
    val displayQueue: String,
    val scaleQueue: String
)
