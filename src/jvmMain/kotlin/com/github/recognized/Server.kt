package com.github.recognized

import com.github.recognized.compile.PsiFacade
import com.github.recognized.dataset.AllCorpuses
import com.github.recognized.dataset.Sample
import com.github.recognized.metrics.FitnessFunction
import com.github.recognized.mutation.AllMutations
import com.github.recognized.mutation.asSequence
import com.github.recognized.runtime.choose
import com.github.recognized.runtime.logger
import com.github.recognized.service.Kernel
import com.github.recognized.service.Metrics
import com.github.recognized.service.State
import com.github.recognized.service.Statistics
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.*
import kotlinx.serialization.list
import org.jetbrains.kotlin.psi.KtElement
import org.kodein.di.generic.instance
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

object Server : CoroutineScope, Disposable by Disposer.newDisposable() {
    private const val GENERATION_SIZE = 4000
    private val log = logger("Server")
    override val coroutineContext: CoroutineContext = Job()

    private var startJob: Job? = null

    private val start = AtomicLong(0L)
    private val generations = AtomicLong(0)
    private val compilations = AtomicLong(0)
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
    @Volatile
    private var runningState = State.Stop
    @Volatile
    private var pauseCallback: (() -> Unit)? = null
    @Volatile
    private var unpauseCallback: (() -> Unit)? = null

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
            uptime = start.get().takeIf { it != 0L }?.let { (System.currentTimeMillis() - it).toInt() / 1000 } ?: 0,
            iterations = generations.get().toInt(),
            compileSuccessRate = if (successfulCompilations.get() != 0L) successfulCompilations.get().toDouble() / compilations.get() else 0.0,
            state = state,
            run = runningState
        )
    }

    private suspend fun checkPause() {
        if (startJob?.isActive != true) {
            throw CancellationException()
        }
        pauseCallback?.let {
            pauseCallback = null
            it()
            suspendCoroutine<Unit> {
                unpauseCallback = {
                    it.resumeWith(Result.success(Unit))
                    runningState = State.Start
                }
            }
        }
    }

    @Synchronized
    fun start() {
        if (runningState != State.Stop) {
            error("Not stopped")
        }
        start.set(System.currentTimeMillis())
        runningState = State.Start
        startJob = launch {

            generation = emptyList()
            compilations.set(0L)
            successfulCompilations.set(0L)

            loadInitialGeneration()

            while (true) {
                if (generation.isEmpty()) {
                    state("Failed, no samples left") {
                    }
                    return@launch
                }

                crossover()
                mutate()
                filter()

                generation = nextGeneration
                nextGeneration = emptyList()
                generations.incrementAndGet()
            }
        }
        runningState = State.Start
    }

    @Synchronized
    suspend fun stop() {
        try {
            if (runningState == State.Stop) {
                error("Not running")
            }
            if (runningState == State.Paused) {
                togglePause()
            }
            startJob?.cancel()
            startJob?.join()
            runningState = State.Stop
            startJob = null
        } catch (ex: Throwable) {
            log.error(ex)
        }
    }

    @Synchronized
    suspend fun togglePause() {
        if (runningState == State.Start) {
            suspendCoroutine<Unit> {
                pauseCallback = {
                    it.resumeWith(Result.success(Unit))
                    runningState = State.Paused
                }
            }
        } else if (runningState == State.Paused) {
            unpauseCallback?.let {
                it()
                unpauseCallback = null
            }
        }
    }

    private fun loadInitialGeneration() {
        val file = File("tmp.kt")
        if (file.exists()) {
            try {
                generation = parse(Sample.serializer().list, file.readText())
                return
            } catch (ex: Throwable) {
                log.info { "Could not load saved generation..." }
            }
        }
        val newGen = mutableListOf<Sample>()
        generation = newGen
        corpuses.samples().asSequence().mapNotNull {
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
                    it.copy(metrics = metrics)
                } else {
                    null
                }
            } else {
                null
            }
        }.take(GENERATION_SIZE).forEach {
            state("Loading initial generation ${newGen.size}") {
                newGen += it
            }
        }
        file.bufferedWriter().use {
            it.write(stringify(Sample.serializer().list, newGen))
        }
    }

    private fun crossover() = state("Crossover #${generations.get() + 1}") {

    }

    private suspend fun mutate() {
        val newGen = mutableListOf<Sample>()
        repeat(generation.size) {
            checkPause()
            state("Mutation(gen=${generations.get() + 1}) #${it + 1}") {
                val mutation = allMutations.all.choose(random)
                val sample = generation[it]
                val afterText = try {
                    mutation.mutate(generation, sample)
                } catch (ex: Throwable) {
                    log.info { ex }
                    return@state
                }
                if (afterText == null) {
                    log.info { "Mutation $mutation failed to mutate $sample" }
                    return@state
                }
                if (afterText == sample.tree?.text) {
                    log.error { "Nothing changed after mutation $mutation for sample $sample" }
                    return@state
                }
                compilations.incrementAndGet()
                val score = fitness.score(afterText)
                if (score == null) {
                    log.error { "Failed to get score" }
                } else {
                    val psiCount = try {
                        facade.getPsi(afterText)?.asSequence()?.count()
                    } catch (ex: Throwable) {
                        log.error(ex)
                        return@state
                    }
                    log.trace { "Score: $score" }
                    log.trace { "After: $afterText" }
                    if (psiCount != null) {
                        val metrics = Metrics(
                            score.jitTime,
                            score.compiled,
                            afterText.length,
                            psiCount
                        )
                        newGen.add(Sample(metrics, nextId(), afterText))
                        log.trace { "New unit value ${metrics.value(kernel)}" }
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
        return "GN-" + generations.get() + "-" + Random.nextLong().let { it }.toString(16)
    }

    private fun filter() {
        val bestCompetitors = (generation + nextGeneration).sortedByDescending {
            it.metrics?.value(kernel)
        }
        val luckySurvivors = (generation.shuffled(random).take(random.nextInt(generation.size / 10)))
        nextGeneration = (luckySurvivors + bestCompetitors).distinctBy { it.id }.take(GENERATION_SIZE)
    }

    private fun <T> state(name: String, fn: () -> T): T {
        state = name
        return fn()
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