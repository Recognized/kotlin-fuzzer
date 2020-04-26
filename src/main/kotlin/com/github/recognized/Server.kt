package com.github.recognized

import com.github.recognized.dataset.Corpus
import com.github.recognized.dataset.Sample
import com.github.recognized.metrics.FitnessFunction
import com.github.recognized.mutation.asSequence
import com.github.recognized.random.FirstElementsChooser
import com.github.recognized.random.IndexChooser
import com.github.recognized.random.IterableChooser
import com.github.recognized.runtime.await
import com.github.recognized.runtime.logger
import com.github.recognized.service.Metrics
import com.github.recognized.service.State
import com.github.recognized.service.Statistics
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.*
import kotlinx.serialization.list
import org.kodein.di.generic.instance
import java.io.File
import kotlin.coroutines.CoroutineContext

private const val GENERATION_SIZE = 4000
private const val OVERGROW = 200

private val log = logger("Server")

object Server : CoroutineScope, Disposable by Disposer.newDisposable() {
    override val coroutineContext: CoroutineContext = Job()

    private var runJob: Job? = null
    private var run: Run? = null
    private var indexLambda = 3.0

    fun generation() = run?.generation.orEmpty()

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
        return run?.stat?.let {
            Statistics(
                uptime = it.start.takeIf { it != 0L }?.let { (System.currentTimeMillis() - it).toInt() / 1000 } ?: 0,
                iterations = it.mutationsCount,
                compileSuccessRate = if (it.successfulCompilations != 0) it.successfulCompilations.toDouble() / it.compilations else 0.0,
                state = it.state,
                run = if (run?.state?.value == State.Stop) State.Pause else (run?.state?.value ?: State.Stop)
            )
        } ?: Statistics(
            uptime = 0,
            iterations = 0,
            compileSuccessRate = 0.0,
            run = State.Stop,
            state = "Idle"
        )
    }

    fun start() {
        if (run?.state?.value == State.Start) {
            error("Should stop current run first")
        }
        val newRun = run?.copy(shouldLoadInitial = false) ?: Run(
            generationSize = GENERATION_SIZE,
            generationOverGrow = OVERGROW,
            loadInitial = { initialGeneration() },
            sampleChooser = FirstElementsChooser(IndexChooser(indexLambda)),
            mutationChooser = IterableChooser()
        )
        run = newRun
        runJob = launch {
            newRun.loop()
        }
    }

    suspend fun stop() {
        runJob?.cancel()
        run?.state?.await(State.Stop)
        run = null
    }

    suspend fun pause() {
        runJob?.cancel()
        run?.state?.await(State.Stop)
    }

    private fun initialGeneration(): List<Sample> {
        val file = File("tmp.kt")
        if (file.exists()) {
            try {
                return parse(Sample.serializer().list, file.readText())
            } catch (ex: Throwable) {
                log.info { "Could not load saved generation..." }
            }
        }
        val corpuses by kodein.instance<Set<Corpus>>()
        val fitness by kodein.instance<FitnessFunction>()
        val generation = mutableListOf<Sample>()
        var count = 0
        corpuses.sortedBy { it::class.simpleName }.asSequence().flatMap { it.samples().asSequence() }.mapNotNull {
            if (coroutineContext[Job]?.isActive != true) {
                throw CancellationException()
            }
            val tree = it.tree
            if (it.metrics == null && tree != null) {
                try {
                    val score = fitness.scoreAvg(tree.file.text, 5)
                    val metrics = Metrics(
                        analyze = score.analyze,
                        generate = score.generate,
                        successful = score.compiled,
                        symbols = tree.file.textLength,
                        psiElements = tree.file.asSequence().count()
                    )
                    it.copy(metrics = metrics)
                } catch (ex: Throwable) {
                    null
                }
            } else {
                null
            }
        }.take(GENERATION_SIZE).forEach {
            generation += it
            log.info {
                "Initial: ${++count} / $GENERATION_SIZE"
            }
        }
        file.bufferedWriter().use {
            it.write(stringify(Sample.serializer().list, generation))
        }
        return generation
    }

    @Synchronized
    fun join() {
        runBlocking {
            runJob?.join()
        }
    }
}

fun main() {
    Server.start()
    Server.join()
}