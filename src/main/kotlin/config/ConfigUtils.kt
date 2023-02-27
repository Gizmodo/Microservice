package config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import config.data.Config
import utils.logger
import java.io.File

object ConfigUtils {
    private val logger by logger()
    fun loadConfig(): Config? {
        return try {
            logger.info { "Loading config" }
            ConfigLoaderBuilder
                .default()
                .addFileSource(File("config.yml").absolutePath)
                .build()
                .loadConfigOrThrow<Config>()

        } catch (ex: Exception) {
            logger.error { ex.message }
            null
        }
    }
}