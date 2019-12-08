package com.github.recognized.metrics

import com.github.recognized.CompileTarget
import com.github.recognized.compile.SimpleMessageCollector
import com.github.recognized.mutation.firstNotNull
import com.github.recognized.runtime.TempFile
import com.github.recognized.runtime.disposing
import com.github.recognized.runtime.logger
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.CommonCompilerPerformanceManager
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.utils.addToStdlib.cast
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.isAccessible

interface FitnessFunction {
    fun score(code: String): Score?
}

data class Score(
    val jitTime: Int,
    val javaClassFind: Int?,
    val compiled: Boolean
)

private val log = logger()

class CompileTimeFitnessFunction<T : CommonCompilerArguments>(
    private val compiler: CLICompiler<T>, private val arguments: () -> T
) : FitnessFunction {
    private val messageCollector = SimpleMessageCollector()
    private var previousAbsoluteJitMark = 0

    override fun score(code: String): Score? {
        val exitCode = doCompile(code)
        log.info { "exit code: $exitCode" }
        return makeScore(exitCode)
    }

    private fun makeScore(exitCode: ExitCode): Score? {
        val jitMark = getJitTimeMark() ?: return log.error { "Cannot find Jit time perf log" }
        val relativeMark = relativizeAbsoluteJitMark(jitMark)

        val javaStat = getJavaClassStat()
        return Score(
            relativeMark,
            javaStat,
            exitCode == ExitCode.OK && !messageCollector.hasErrors()
        )
    }

    private fun doCompile(code: String): ExitCode {
        disposing {
            messageCollector.clear()
            compiler.clear()
            val args = arguments()
            compiler.parseArguments(arrayOf("${TempFile(code, it).dir.toAbsolutePath()}"), args)
            val exitCode = compiler.exec(messageCollector, Services.EMPTY, args)
            messageCollector.others()
            reportErrors()
            return exitCode
        }
    }

    private fun reportErrors() {
        messageCollector.errors().forEach {
            log.error { it }
        }
    }

    private fun relativizeAbsoluteJitMark(absoluteMark: Int): Int {
        return (absoluteMark - previousAbsoluteJitMark).also {
            previousAbsoluteJitMark = absoluteMark
        }
    }

    private fun getJitTimeMark(): Int? {
        return perf().find(JIT_TIME_MARK)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun getJavaClassStat(): Int? {
        return perf().find(JAVA_CLASS_FIND_STAT)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun perf(): List<String> = messageCollector.others().filter { it.startsWith("PERF") }

    companion object {
        private val JIT_TIME_MARK = "JIT time is ([0-9]+) ms".toRegex()
        private val JAVA_CLASS_FIND_STAT = "Java class performed ([0-9]+) times".toRegex()
    }
}

fun Iterable<String>.find(regex: Regex): MatchResult? {
    return firstNotNull {
        regex.find(it)
    }
}

fun CommonCompilerArguments.addSource(path: String) {
    when (this) {
        is K2JVMCompilerArguments -> {
            classpath = listOfNotNull(path, classpath).joinToString(separator = File.pathSeparator)
        }
        else -> error("Cannot configure sources for ${this::class.java.simpleName}")
    }
}


private fun CLICompiler<*>.clear() {
    this::class.declaredMembers
        .first { "performance" in it.name }
        .also {
            it.isAccessible = true
        }.call(this)
        .cast<CommonCompilerPerformanceManager>().let { manager ->
            CommonCompilerPerformanceManager::class.declaredMembers
                .first { it.name == "measurements" }
                .also { it.isAccessible = true }
                .call(manager).cast<MutableList<*>>()
                .clear()
        }
}

fun getCompileTimeFitnessFunction(target: CompileTarget, kotlinHome: String, classpath: List<String>): FitnessFunction {
    return when (target) {
        CompileTarget.Jvm -> {
            CompileTimeFitnessFunction(
                K2JVMCompiler()
            ) producer@{
                K2JVMCompilerArguments().apply {
                    reportPerf = true
                    this.kotlinHome = kotlinHome
                    this.destination = "outDir"
                    version = true
                }
            }
        }
        else -> error("Unsupported compile target $target")
    }
}