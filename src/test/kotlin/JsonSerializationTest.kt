import enums.DisplayLine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.DisplayEvent
import org.junit.jupiter.api.Test
import utils.logger

class JsonSerializationTest {
    companion object {
        val logger by logger()
    }

    @Test
    fun serialEnumClass() {
        val deObject: DisplayEvent = DisplayEvent.ClearDisplay
        val deDataClass: DisplayEvent = DisplayEvent.WriteLine(
            DisplayLine.FirstScroll,
            "test"
        )
        val encoded = Json.encodeToString(deDataClass)
        logger.info { encoded }
        val decoded = Json.decodeFromString<DisplayEvent>(encoded)
        when (decoded) {
            DisplayEvent.ClearDisplay -> {
                logger.info { "Был передан объект ClearDisplay" }
            }

            is DisplayEvent.WriteLine -> {
                logger.info { "Был передан класс WriteLine" }
            }
        }
        logger.info { decoded }
    }
}