import org.junit.jupiter.api.Test
import utils.Converter.convertMessage
import kotlin.test.assertEquals


class MainKtTest {
    @Test
    fun `should convert russian unicode А to ASCII А`() {
        val result = convertMessage("А")
        val item = result[0].toUByte()
        assertEquals(item, 0x80.toUByte(), "Символу А должен соответствовать код 0x80 (128)")
    }
}