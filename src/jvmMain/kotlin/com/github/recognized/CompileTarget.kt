package com.github.recognized

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles

enum class CompileTarget {
    Jvm, Js, Native
}

val CompileTarget.configurationFiles
    get() = when (this) {
        CompileTarget.Jvm -> EnvironmentConfigFiles.JVM_CONFIG_FILES
        CompileTarget.Js -> EnvironmentConfigFiles.JS_CONFIG_FILES
        CompileTarget.Native -> EnvironmentConfigFiles.NATIVE_CONFIG_FILES
    }

