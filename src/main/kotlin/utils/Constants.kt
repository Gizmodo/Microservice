package utils

object Constants {
    val displayName = "POSua LPOS-II-VFD USB CDC"
    val barcodeName = "Handheld Barcode Scanner"
    val RABBITMQ_HEART_BEAT_TIMEOUT = 60
    val RABBITMQ_NETWORK_RECOVER_INTERVAL = 1L
    val RABBITMQ_CONNECTION_TIMEOUT = 10L

    /***
     * Exchange for LPOS_VFD Display
     */
    val RABBITMQ_DISPLAY_EXCHANGE = "DisplayExchange"
    val RABBITMQ_DISPLAY_QUEUE = "DisplayQueue"
    val RABBITMQ_DISPLAY_ROUTING_KEY = "DisplayRoutingKey"

    val RABBITMQ_BARCODE_QUEUE = "BarcodeQueue"
    val RABBITMQ_BARCODE_ROUTING_KEY = "BarcodeRoutingKey"
}