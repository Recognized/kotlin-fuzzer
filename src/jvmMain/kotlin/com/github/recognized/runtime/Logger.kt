package com.github.recognized.runtime

import io.ktor.util.error
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun logger(name: String): com.github.recognized.runtime.Logger {
    return Logger(LoggerFactory.getLogger(name))
}

class Logger(private val parent: Logger) {

    fun info(msg: () -> Any) {
        if (parent.isInfoEnabled) {
            parent.info(msg().toString())
        }
    }

    fun debug(msg: () -> Any) {
        if (parent.isDebugEnabled) {
            parent.debug(msg().toString())
        }
    }

    fun error(msg: () -> Any): Nothing? {
        if (parent.isErrorEnabled) {
            parent.error(msg().toString())
        }
        return null
    }

    fun trace(msg: () -> Any) {
        if (parent.isTraceEnabled) {
            parent.trace(msg().toString())
        }
    }

    fun error(ex: Throwable) {
        if (parent.isErrorEnabled) {
            parent.error(ex)
        }
    }
}