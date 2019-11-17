package com.github.recognized

import com.github.recognized.compile.PsiFacade
import com.github.recognized.runtime.logger
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler

val app = Disposer.newDisposable()
private val log = logger()

fun main() {

    log.info {
        PsiFacade(app, CompileTarget.Jvm).getPsi(
            """
                import kotlin.text
                
                println(Charset.UTF-8)
            """.trimIndent()
        )
    }

    val compiler = K2JVMCompiler()

//    compiler.exec()

    Disposer.dispose(app)
}