package com.github.recognized.dataset

import com.github.recognized.compile.PsiFacade
import org.jetbrains.kotlin.psi.KtElement
import org.kodein.di.Kodein
import org.kodein.di.generic.*

interface Corpus {
    fun samples(): List<Sample>
}

interface Sample {
    val tree: KtElement?
}

class LazySample(private val facade: PsiFacade, private val file: String) : Sample {
    override val tree: KtElement? get() = facade.getPsi(file)
}

fun Kodein.MainBuilder.corpuses() {
    bind() from setBinding<Corpus>()
    bind<Corpus>().inSet() with singleton { YouTrackCorpus(instance()) }
    bind<Corpus>().inSet() with singleton { KotlinTestsCorpus(instance()) }
    bind() from singleton { AllCorpuses(instance()) }
}

class AllCorpuses(private val data: Set<Corpus>) : Corpus {
    private val allSamples by lazy { data.flatMap { it.samples() } }

    override fun samples(): List<Sample> = allSamples
}