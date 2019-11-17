package com.github.recognized.runtime

import io.ktor.util.error
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun logger(name: () -> String? = { null }): com.github.recognized.runtime.Logger {
    return Logger(LoggerFactory.getLogger(name() ?: name::class.java.name.substringBefore('$')))
}

class Logger(val parent: Logger) {

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

    fun error(msg: () -> Any) {
        if (parent.isErrorEnabled) {
            parent.error(msg().toString())
        }
    }

    fun error(ex: Throwable) {
        if (parent.isErrorEnabled) {
            parent.error(ex)
        }
    }
}