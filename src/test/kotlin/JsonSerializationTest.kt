import enums.DisplayCursorMode
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
        val deChangeCursor: DisplayEvent = DisplayEvent.ChangeCursor(
            DisplayCursorMode.Blink
        )
        val encoded: String = Json.encodeToString(deChangeCursor)
        logger.info { encoded }
        val decoded: DisplayEvent = Json.decodeFromString<DisplayEvent>(encoded)
        when (decoded) {
            DisplayEvent.ClearDisplay -> {
                logger.info { "Был передан объект ClearDisplay" }
            }

            is DisplayEvent.WriteLine -> {
                logger.info { "Был передан класс WriteLine" }
            }

            is DisplayEvent.ChangeCursor -> {
                logger.info { "Был передан объект ChangeCursor" }
            }
        }
        logger.info { decoded }
    }
}