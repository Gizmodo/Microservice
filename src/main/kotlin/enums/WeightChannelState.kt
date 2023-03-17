package enums

sealed class WeightChannelState {
    /**
     * бит 0 - признак фиксации веса
     */
    data class Fixed(val weight: String) : WeightChannelState()

    /**
     * бит 1 - признак работы автонуля
     */
    data object AutoNull : WeightChannelState()

    /**
     *  бит 2 - "0"- канал выключен, "1"- канал включен.
     */
    data object ChannelDown : WeightChannelState()

    /**
     *  бит 3 - признак тары
     */
    data object Tare : WeightChannelState()

    /**
     *  бит 4 - признак успокоения веса
     */
    data object StableWeight : WeightChannelState()

    /**
     *  бит 5 - ошибка автонуля при включении
     */
    data object AutoNullOnPowerOn : WeightChannelState()

    /**
     *  бит 6 - перегрузка по весу
     */
    data object Overload : WeightChannelState()

    /**
     *  бит 7 - ошибка при получении измерения
     */
    data object ErrorMeasurement : WeightChannelState()

    /**
     *  бит 8 - весы недогружены
     */
    data object Underload : WeightChannelState()

    /**
     *  бит 9 - нет ответа от АЦП
     */
    data object ErrorADC : WeightChannelState()

    /**
     *  бит 10..бит 15 - Reserved.
     * Вес (4 байта со знаком), диапазон -НПВ..НПВ.
     * Тара (2 байта), диапазон 0..ТАРА (значение задано в характеристиках канала).
     * Флаги: бит 7: состояние денежного ящика 1 — открыт, 0 — закрыт.
     */
    data object Reserved : WeightChannelState()

}
