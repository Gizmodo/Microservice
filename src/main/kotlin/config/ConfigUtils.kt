package config

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import config.data.Config
import config.helper.ConfigFailureProblem
import config.helper.Problem
import utils.logger
import java.io.File

object ConfigUtils {
    private val logger by logger()
    fun getConfig(): Either<Problem, Config> {
        return try {
            logger.info { "Loading config" }
            ConfigLoaderBuilder
                .default()
                .addFileSource(File("config.yml").absolutePath)
                .build()
                .loadConfigOrThrow<Config>().right()
        } catch (ex: Exception) {
            logger.error { ex.message }
            if (ex.message.isNullOrEmpty()) {
                ConfigFailureProblem("Error occurred").left()
            } else {
                ConfigFailureProblem(ex.message.toString()).left()
            }
        }
    }
}