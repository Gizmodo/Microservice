package enums

enum class Symbols(val value: Byte) {
    ENQ(0x05),
    STX(0x02),
    ACK(0x06),
    NAK(0x15),
    ERR(0x00);

    companion object {
        infix fun from(value: Byte): Symbols {
            return values().firstOrNull { it.value == value } ?: ERR
        }
    }
}

enum class ScaleCommand(val value: Byte) {
    GETWEIGHT(0x3A);

    companion object {
        infix fun from(value: Byte): ScaleCommand? {
            return values().firstOrNull { it.value == value }
        }
    }
}