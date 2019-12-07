package com.github.recognized

import com.github.recognized.compile.PsiFacade
import com.github.recognized.compile.hasErrorBelow
import com.github.recognized.dataset.AllCorpuses
import com.github.recognized.metrics.FitnessFunction
import com.github.recognized.mutation.AllMutations
import com.github.recognized.runtime.choose
import com.github.recognized.runtime.logger
import com.github.recognized.service.Statistics
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.*
import org.jetbrains.kotlin.psi.KtElement
import org.kodein.di.generic.instance
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

object Server : CoroutineScope, Disposable by Disposer.newDisposable() {
    private const val ITERATIONS = 5000
    private val log = logger()
    override val coroutineContext: CoroutineContext = Job()

    private var startJob: Job? = null

    private val start = AtomicLong()
    private val iterations = AtomicLong()
    private val compilations = AtomicLong()
    private val successfulCompilations = AtomicLong()

    fun stat(): Statistics {
        return Statistics(
            start.get().takeIf { it != 0L }?.let { System.currentTimeMillis() - it } ?: 0L,
            iterations.get(),
            compilations.get() / successfulCompilations.get().toDouble()
        )
    }

    @Synchronized
    fun start() {
        start.set(System.currentTimeMillis())
        startJob = launch {
            val allMutations by kodein.instance<AllMutations>()
            val random by kodein.instance<Random>()
            val data by kodein.instance<AllCorpuses>()
            val facade by kodein.instance<PsiFacade>()
            val fitness by kodein.instance<FitnessFunction>()

            while (true) {
                iterations.incrementAndGet()
                val mutation = allMutations.all.choose(random)
                val sample = data.samples().choose(random)
                val before = sample.tree?.copy()
                if (before == null) {
                    log.info { "Failing element in corpus, skip" }
                    continue
                }
                log.info { "Before: ${before.text}" }
                val replace = before.choose(random)
                val afterText = try {
                    mutation.mutate(data, replace as KtElement)
                    before.text
                } catch (ex: Throwable) {
                    log.info { ex }
                    continue
                }
                compilations.incrementAndGet()
                val score = fitness.score(afterText)
                if (score == null) {
                    log.error { "Failed to get score" }
                } else {
                    log.info { "Score: $score" }
                    log.info { "After: $afterText" }
                    if (score.compiled) {
                        successfulCompilations.incrementAndGet()
                    }
                }
            }
        }
    }

    @Synchronized
    fun join() {
        runBlocking {
            startJob?.join()
        }
    }
}

fun main() {
    Server.start()
    Server.join()
}