package utils

object Converter {
    fun convertMessage(message: String): ByteArray {
        val rangeRussian = ('А'..'я') + ('Ё'..'Ё') + ('ё'..'ё')
        val rangeRussianSmall = ('р'..'я') + ('ё'..'ё')
        val hexChars = message.toCharArray()
        val allByteArray = ArrayList<Byte>()

        for (item: Char in hexChars) {
            if (item in rangeRussian) {
                val t: Char = if (item in rangeRussianSmall) {
                    //р...я and ё
                    if (item.equals('ё')) {
                        item - 0x3AC
                    } else {
                        item - 0x360
                    }
                } else {
                    //А...п
                    if (item.equals('Ё')) {
                        item - 0x37C
                    } else {
                        item - 0x390
                    }
                }
//            println("item: ${item.code} $item t: ${t.code} -> ${t}")
                allByteArray.add(t.code.toByte())
            } else {
                allByteArray.add(item.code.toByte())
            }
        }
//    println(allByteArray)
        return allByteArray.toByteArray()
    }
}