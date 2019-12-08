package com.github.recognized

import com.github.recognized.compile.PsiFacade
import com.github.recognized.dataset.*
import com.github.recognized.metrics.FitnessFunction
import com.github.recognized.mutation.AllMutations
import com.github.recognized.mutation.asSequence
import com.github.recognized.runtime.choose
import com.github.recognized.runtime.logger
import com.github.recognized.service.Kernel
import com.github.recognized.service.Metrics
import com.github.recognized.service.Statistics
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.psi.KtElement
import org.kodein.di.generic.instance
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

object Server : CoroutineScope, Disposable by Disposer.newDisposable() {
    private const val GENERATION_SIZE = 1000
    private val log = logger()
    override val coroutineContext: CoroutineContext = Job()

    private var startJob: Job? = null

    private val start = AtomicLong()
    private val generations = AtomicLong()
    private val compilations = AtomicLong()
    private val successfulCompilations = AtomicLong()
    private val corpuses by kodein.instance<AllCorpuses>()
    private val allMutations by kodein.instance<AllMutations>()
    private val random by kodein.instance<Random>()
    private val facade by kodein.instance<PsiFacade>()
    private val fitness by kodein.instance<FitnessFunction>()
    private val kernel by kodein.instance<Kernel>()

    private var generation: List<Sample> = emptyList()
    private var nextGeneration: List<Sample> = mutableListOf()

    @Volatile
    private var state: String = "Idle"

    fun generation() = generation

    init {
        Disposer.register(this, Disposable {
            File("outDir").walk().forEach {
                if (it.isFile) {
                    try {
                        it.delete()
                    } catch (ex: Throwable) {
                        // ignore
                        log.error(ex)
                    }
                }
            }
        })
    }

    fun stat(): Statistics {
        return Statistics(
            start.get().takeIf { it != 0L }?.let { System.currentTimeMillis() - it } ?: 0L,
            generations.get(),
            compilations.get() / successfulCompilations.get().toDouble(),
            state
        )
    }

    @Synchronized
    fun start() {
        start.set(System.currentTimeMillis())
        startJob = launch {

            loadInitialGeneration()

            while (true) {

                crossover()
                mutate()

                generation = nextGeneration
                nextGeneration = emptyList()
                generations.incrementAndGet()
            }
        }
    }

    private fun loadInitialGeneration() = state("Loading initial generation") {
        generation = corpuses.samples().mapNotNull {
            val tree = it.tree
            if (it.metrics == null && tree != null) {
                val score = fitness.score(tree.text)
                if (score != null) {
                    val metrics = Metrics(
                        score.jitTime,
                        score.compiled,
                        tree.text.length,
                        tree.asSequence().count()
                    )
                    SampleWithMetrics(metrics, it)
                } else {
                    null
                }
            } else {
                null
            }
        }.take(GENERATION_SIZE)
    }

    private fun crossover() = state("Crossover #${generations.get() + 1}") {

    }

    private fun mutate() {
        val newGen = mutableListOf<Sample>()
        repeat(generation.size) {
            state("Mutation(gen=${generations.get() + 1}) #${it + 1}") {
                val mutation = allMutations.all.choose(random)
                val sample = generation[it]
                val before = sample.tree?.copy()
                if (before == null) {
                    log.info { "Failing element in corpus, skip" }
                    return@state
                }
                log.info { "Before: ${before.text}" }
                val replace = before.choose(random)
                val afterText = try {
                    mutation.mutate(generation, replace as KtElement)
                    before.text
                } catch (ex: Throwable) {
                    log.info { ex }
                    return@state
                }
                compilations.incrementAndGet()
                val score = fitness.score(afterText)
                if (score == null) {
                    log.error { "Failed to get score" }
                } else {
                    val psiCount = facade.getPsi(afterText)?.asSequence()?.count()
                    log.info { "Score: $score" }
                    log.info { "After: $afterText" }
                    if (psiCount != null) {
                        val metrics = Metrics(
                            score.jitTime,
                            score.compiled,
                            afterText.length,
                            psiCount
                        )
                        newGen.add(SampleWithMetrics(metrics, IdSample(nextId(), LazySample(facade, afterText))))
                        log.info { "New unit value ${metrics.value(kernel)}" }
                    }
                    if (score.compiled) {
                        successfulCompilations.incrementAndGet()
                    }
                }
            }
        }
        nextGeneration = nextGeneration + newGen
    }

    private fun nextId(): String {
        return "GN-" + generations.get() + "-" + Random.nextLong().let { it }.toString(64)
    }

    private fun filter() {
        val luckySurvivors = (generation.shuffled(random).take(random.nextInt(generation.size / 10)))
        val bestCompetitors = (generation + nextGeneration).sortedByDescending {
            it.metrics?.value(kernel)
        }
        nextGeneration = (luckySurvivors + bestCompetitors).take(GENERATION_SIZE)
    }

    private fun <T> state(name: String, fn: () -> T): T {
        val oldState = state
        state = name
        try {
            return fn()
        } finally {
            state = oldState
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