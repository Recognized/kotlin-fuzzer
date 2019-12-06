package com.github.recognized.service

import com.github.recognized.RPCService
import kotlinx.serialization.Serializable


interface Fuzzer : RPCService {

    suspend fun stat(): Statistics

    suspend fun start()
}

@Serializable
data class Statistics(
    val uptime: Long,
    val iterations: Long,
    val compileSuccessRate: Double
)

@Serializable
data class Metrics(
    val jitTime: Int,
    val successful: Boolean,
    val symbols: Int,
    val psiElements: Int
)