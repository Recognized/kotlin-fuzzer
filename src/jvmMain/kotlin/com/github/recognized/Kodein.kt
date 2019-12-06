package com.github.recognized

import com.github.recognized.compile.PsiFacade
import com.github.recognized.dataset.corpuses
import com.github.recognized.metrics.FitnessFunction
import com.github.recognized.metrics.getCompileTimeFitnessFunction
import com.github.recognized.mutation.mutations
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import org.kodein.di.generic.with
import java.net.URLClassLoader
import kotlin.random.Random

const val KOTLIN_HOME = "kotlinHome"
const val CLASSPATH = "classpath"

fun Kodein.MainBuilder.mainKodein() {
    bind() from singleton { CompileTarget.Jvm }
    bind<PsiFacade>() with singleton { PsiFacade(Server, instance()) }
    bind<Random>() with singleton { Random(1999) }
    bind<List<String>>(CLASSPATH) with singleton { setupClasspath(instance()) }
    constant(KOTLIN_HOME) with "/home/recog/Apps/Idea.2019.1.3/idea-IU-191.7479.19/plugins/Kotlin/"
    bind<FitnessFunction>() with singleton {
        getCompileTimeFitnessFunction(
            instance(),
            instance(KOTLIN_HOME),
            instance(CLASSPATH)
        )
    }

    mutations()

    corpuses()
}

fun setupClasspath(target: CompileTarget): List<String> {
    return when (target) {
        CompileTarget.Jvm -> {
            val classLoader = (target::class.java.classLoader as URLClassLoader)
            val requiredJars = setOf(
                "kotlin-stdlib-jdk8",
                "kotlin-reflect"
            )
            println(classLoader.urLs.toList())
            classLoader.urLs.filter { url ->
                requiredJars.any { it in url.toString() }
            }.map { it.toURI().path }
        }
        else -> unsupportedCompileTarget(target)
    }
}

fun unsupportedCompileTarget(target: CompileTarget): Nothing = error("Unsupported compile target $target")

val kodein = Kodein {
    mainKodein()
}