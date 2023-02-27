package enums

fun ByteArray.toHexString() = joinToString(" ") { (0xFF and it.toInt()).toString(16).padStart(2, '0') }
fun littleEndianConversion(bytes: ByteArray): Int {
    var result = 0
    for (i in bytes.indices) {
        result = result or (bytes[i].toInt() shl 8 * i)
    }
    return result
}
@JvmOverloads
fun ByteArray.toHexString2(separator: CharSequence = " ", prefix: CharSequence = "[", postfix: CharSequence = "]") =
    this.joinToString(separator, prefix, postfix) {
        String.format("0x%02X", it)
    }

fun Int.toBinary(len: Int): String {
    return String.format("%" + len + "s", this.toString(2)).replace(" ".toRegex(), "0")
}

fun isKthBitSet(n: Int, k: Int): Boolean {
    return (n and (1 shl k) != 0)
}

fun UByte.toBinary(): String {
    return buildString(8) {
        val binaryStr = this@toBinary.toString(2)
        val countOfLeadingZeros = 8 - binaryStr.count()
        repeat(countOfLeadingZeros) {
            append(0)
        }
        append(binaryStr)
    }
}