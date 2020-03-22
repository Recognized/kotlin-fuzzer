package com.github.recognized

import com.github.recognized.compile.PsiFacade
import com.github.recognized.dataset.AllCorpuses
import com.github.recognized.dataset.Sample
import com.github.recognized.dataset.sampleComparator
import com.github.recognized.metrics.FitnessFunction
import com.github.recognized.metrics.Score
import com.github.recognized.metrics.`±`
import com.github.recognized.mutation.Mutation
import com.github.recognized.mutation.MutationInfo
import com.github.recognized.mutation.asSequence
import com.github.recognized.random.Chooser
import com.github.recognized.runtime.Property
import com.github.recognized.runtime.logger
import com.github.recognized.service.Kernel
import com.github.recognized.service.Metrics
import com.github.recognized.service.State
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.jetbrains.kotlin.cfg.pseudocode.getContainingPseudocode
import org.kodein.di.generic.instance
import kotlin.coroutines.coroutineContext
import kotlin.math.pow
import kotlin.random.Random

data class RunStat(
    var start: Long = 0,
    var generations: Int = 0,
    var compilations: Int = 0,
    var successfulCompilations: Int = 0,
    val compilationErrors: MutableMap<String, Int> = mutableMapOf(),
    var mutationsCount: Int = 0,
    var state: String = "Idle"
)

private val log = logger("Run")

data class Run(
    var generation: List<Sample> = emptyList(),
    val stat: RunStat = RunStat(),
    val shouldLoadInitial: Boolean = true,
    val mutationChooser: Chooser<Iterable<Mutation>, Mutation>,
    val sampleChooser: Chooser<Iterable<Sample>, Sample>,
    val generationSize: Int,
    val generationOverGrow: Int,
    val repeatCount: Int = 5,
    val loadInitial: suspend () -> List<Sample> = { emptyList() }
) {
    private val mutations by kodein.instance<Set<Mutation>>()
    private val random by kodein.instance<Random>()
    private val facade by kodein.instance<PsiFacade>()
    private val fitness by kodein.instance<FitnessFunction>()
    private val kernel by kodein.instance<Kernel>()
    private val cmp = sampleComparator(kernel)
    val state = Property(State.Stop)

    init {
        generation = generation.sortedWith(cmp)
        assert(mutations.isNotEmpty())
    }

    suspend fun loop() {
        try {
            state.value = State.Start

            if (shouldLoadInitial) {
                generation = loadInitial()
            }

            if (generation.isEmpty()) {
                error("Empty initial generation")
            }

            while (true) {

                generation = generation.sortedWith(cmp)

                checkCancelled()
                stat.compilations++

                val info = try {
                    mutate()
                } catch (ex: Throwable) {
                    compilationError(ex.message)
                    continue
                }

                checkCancelled()

                val metrics = try {
                    score(info.result)
                } catch (ex: Throwable) {
                    continue
                }

                if (metrics.successful) {
                    stat.successfulCompilations++
                }

                checkCancelled()

                val mutated = Sample(metrics, nextId(), info.result)
                mutated.parent = info
                generation += mutated

                if (generation.size > generationSize + generationOverGrow) {
                    filter()
                }
            }
        } finally {
            state.value = State.Stop
        }
    }

    private fun compilationError(message: String?) {
        if (message == null) {
            return
        }
        val map = stat.compilationErrors
        map[message] = 1 + (map[message] ?: 0)
    }

    private suspend fun checkCancelled() {
        if (coroutineContext[Job]?.isActive != true) {
            throw CancellationException()
        }
    }

    private fun mutate(): MutationInfo {
        return state("Mutation #${stat.mutationsCount++}") {
            val mutation = mutationChooser.choose(random, mutations)!!
            val sample = sampleChooser.choose(random, generation)!!
            val code = mutation.mutate(generation, sample) ?: error("Mutation $mutation failed to mutate")
            if (code == sample.tree?.text) {
                error("Nothing changed after mutation $mutation")
            }
            MutationInfo(sample, mutation, code)
        }
    }

    private fun score(code: String): Metrics {
        val score = fitness.scoreAvg(code, repeatCount, this::compilationError)
        val psiCount = facade.getPsi(code)?.asSequence()?.count() ?: error("Could not parse code after mutation")
        return Metrics(
            analyze = score.analyze,
            generate = score.generate,
            successful = score.compiled,
            symbols = code.length,
            psiElements = psiCount
        )
    }

    private fun filter() {
        val luckySurvivors = (generation.shuffled(random).take(random.nextInt(generation.size / 10)))
        generation = (luckySurvivors + generation).distinctBy { it.id }.take(generationSize)
    }

    private fun nextId(): String {
        return "GN-" + stat.generations + "-" + stat.mutationsCount
    }

    private inline fun <T> state(name: String, fn: () -> T): T {
        stat.state = name
        return fn()
    }
}

fun FitnessFunction.scoreAvg(
    code: String,
    repeatCount: Int = 5,
    statReporter: (String) -> Unit = { error(it) }
): Score {
    return (1..repeatCount).map {
        score(code, statReporter)
    }.let {
        val analyzeAvg = it.map { it.analyze.value }.average().toInt()
        val generateAvg = it.map { it.generate.value }.average().toInt()
        Score(
            analyze = analyzeAvg `±` (it.sumByDouble {
                (it.analyze.value - analyzeAvg).toDouble().pow(2)
            } / it.size).pow(0.5).toInt(),
            generate = generateAvg `±` (it.sumByDouble {
                (it.generate.value - generateAvg).toDouble().pow(2)
            } / it.size).pow(0.5).toInt(),
            compiled = it.all { it.compiled }
        )
    }
}