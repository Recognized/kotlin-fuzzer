package com.github.recognized.service

import com.github.recognized.RPCService
import kotlinx.serialization.Serializable


interface Fuzzer : RPCService {

    suspend fun stat(): Statistics

    suspend fun pause()

    suspend fun start()

    suspend fun stop()

    suspend fun generation(offset: Int, count: Int, sortBy: SortOrder, onlyMutated: Boolean): List<Snippet>
}

@Serializable
enum class SortOrder {
    Score, Analyze, Generate, PsiElement, Symbols, Name
}

@Serializable
enum class State {
    Stop, Start, Pause
}

@Serializable
data class Statistics(
    val uptime: Int,
    val run: State,
    val iterations: Int,
    val compileSuccessRate: Double,
    val state: String
)

@Serializable
data class Snippet(
    val id: String,
    val metrics: Metrics,
    val value: Int
)

@Serializable
data class IntWithDispersion(
    val value: Int,
    val d: Int
) {
    override fun toString(): String {
        return "$value±$d"
    }
}

infix fun Int.plusMinus(other: Int): IntWithDispersion = IntWithDispersion(this, other)

@Serializable
data class Metrics(
    val analyze: IntWithDispersion,
    val generate: IntWithDispersion,
    val successful: Boolean,
    val symbols: Int,
    val psiElements: Int
) {
    fun value(kernel: Kernel): Int {
        return (kernel.fn(symbols.toDouble()) * ((analyze.value * generate.value) * 10.0 / psiElements)).toInt()
    }

    fun show(kernel: Kernel): String {
        return toString() + ", score: ${value(kernel)}"
    }
}

class Kernel(val name: String, val fn: (Double) -> Double)
