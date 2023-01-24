import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import model.Data
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class JsonTest {

    @Test
    fun `check that json deserialisation works`() {
        val obj = Json.decodeFromString<Data>("""{"a":42, "b": "str"}""")
        assertTrue(obj.a.equals(42), "Actual value is ${obj.a}")
        assertTrue(obj.b.equals("str"), "Actual value is ${obj.b}")
    }
}