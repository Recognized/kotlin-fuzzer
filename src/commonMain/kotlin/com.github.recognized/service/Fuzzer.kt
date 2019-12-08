package com.github.recognized.service

import com.github.recognized.RPCService
import kotlinx.serialization.Serializable


interface Fuzzer : RPCService {

    suspend fun stat(): Statistics

    suspend fun start()

    suspend fun generation(offset: Int, count: Int, sortBy: SortOrder): List<Snippet>
}

@Serializable
enum class SortOrder {
    Score
}

@Serializable
data class Statistics(
    val uptime: Long,
    val iterations: Long,
    val compileSuccessRate: Double,
    val state: String
)

@Serializable
data class Snippet(
    val id: String,
    val metrics: Metrics,
    val value: Long
)

@Serializable
data class Metrics(
    val jitTime: Int,
    val successful: Boolean,
    val symbols: Int,
    val psiElements: Int
) {
    fun value(kernel: Kernel): Long {
        return (kernel.fn(symbols.toDouble()) * (jitTime * 10000.0 / psiElements)).toLong()
    }
}

class Kernel(val name: String, val fn: (Double) -> Double)
