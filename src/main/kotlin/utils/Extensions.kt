package utils

import mu.KLogger
import mu.KotlinLogging

fun <R : Any> R.logger(): Lazy<KLogger> {
    return lazy { KotlinLogging.logger(getClassName(this::class.java)) }
}

fun <T : Any> getClassName(clazz: Class<T>): String {
    return clazz.name.removeSuffix("\$Companion")
}