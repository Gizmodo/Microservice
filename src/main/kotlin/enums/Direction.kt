package test.enums

enum class Direction(val direction: Byte) {
    Up(0x41),
    Down(0x42),
    Right(0x43),
    Left(0x44),
    TopLeft(0x48),
    CurrentLineStart(0x4C),
    CurrentLineEnd(0x52),
    BottomEnd(0x4B)
}