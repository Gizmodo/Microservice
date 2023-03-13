package config.data

data class DevicesPort(
    val cubic: BasePort,
    val handheld: BasePort,
    val scales: BasePort,
    val scalesScanner: BasePort,
    val display: BasePort,
)

data class BasePort(
    val enabled: Boolean,
    val port: String
)
