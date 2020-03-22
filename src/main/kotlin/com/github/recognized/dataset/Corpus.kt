package com.github.recognized.dataset

import com.github.recognized.compile.PsiFacade
import com.github.recognized.kodein
import com.github.recognized.mutation.MutationInfo
import com.github.recognized.service.Kernel
import com.github.recognized.service.Metrics
import kotlinx.serialization.ContextualSerialization
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import org.jetbrains.kotlin.psi.KtElement
import org.kodein.di.Kodein
import org.kodein.di.generic.*

interface Corpus {
    fun samples(): List<Sample>
}

@Serializable
data class Type(
    val start: Int,
    val end: Int,
    val value: String
)

@Serializable
data class Sample(
    val metrics: Metrics?,
    val id: String?,
    val file: String
) {
    private val facade by kodein.instance<PsiFacade>()
    @Transient
    var parent: MutationInfo? = null

    val tree: KtElement? get() = facade.getPsi(file)
}

fun Kodein.MainBuilder.corpuses() {
    bind() from setBinding<Corpus>()
    bind<Corpus>().inSet() with singleton { YouTrackCorpus(instance()) }
    bind<Corpus>().inSet() with singleton { KotlinTestsCorpus(instance()) }
    bind() from singleton { AllCorpuses(instance()) }
}

class AllCorpuses(private val data: Set<Corpus>) : Corpus {
    private val allSamples by lazy { data.sortedBy { it::class.simpleName }.flatMap { it.samples() } }

    override fun samples(): List<Sample> = allSamples
}

fun sampleComparator(kernel: Kernel): Comparator<Sample> {
    val cmp: Comparator<Sample> = compareBy { it.metrics?.value(kernel) ?: -1 }
    return cmp.reversed()
}