package com.github.recognized.service

import com.github.recognized.RPCService
import kotlinx.serialization.Serializable


interface Fuzzer : RPCService {

    suspend fun stat(): Statistics

    suspend fun pause()

    suspend fun start()

    suspend fun stop()

    suspend fun generation(offset: Int, count: Int, sortBy: SortOrder): List<Snippet>
}

@Serializable
enum class SortOrder {
    Score
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
data class Metrics(
    val analyze: Int,
    val generate: Int,
    val successful: Boolean,
    val symbols: Int,
    val psiElements: Int
) {
    fun value(kernel: Kernel): Int {
        return (kernel.fn(symbols.toDouble()) * ((analyze * generate) * 10.0 / psiElements)).toInt()
    }
}

class Kernel(val name: String, val fn: (Double) -> Double)
