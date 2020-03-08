package com.github.recognized.mutation

import com.github.recognized.dataset.Sample
import com.github.recognized.kodein
import com.github.recognized.random.Chooser
import com.github.recognized.random.FineTunedSubtreeChooser
import com.github.recognized.random.SimpleSubtreeChooser
import com.intellij.psi.PsiElement
import org.kodein.di.Kodein
import org.kodein.di.generic.*

interface Mutation {
    val name: String get() = this::class.java.simpleName

    fun mutate(corpus: List<Sample>, sample: Sample): String?
}

fun Kodein.MainBuilder.mutations() {
    bind() from setBinding<Mutation>()
    bind<Chooser<PsiElement, PsiElement>>() with singleton { FineTunedSubtreeChooser(SimpleSubtreeChooser(0.2)) }

    bind<Mutation>().inSet() with singleton { Replace(instance(), instance()) }
    bind<Mutation>().inSet() with singleton { Add(instance(), instance(), instance()) }
}

class MutationInfo(val source: Sample, val mutation: Mutation, val result: String)

fun showChain(sample: Sample): String {

    val kernel by kodein.instance<com.github.recognized.service.Kernel>()

    fun depth(sample: Sample): Int = sample.parent?.let { depth(it.source) + 1 } ?: 0

    fun show(sample: Sample, depth: Int): String {
        return buildString {
            sample.parent?.let {
                append(show(it.source, depth - 1))
                append("\n----END OF FILE----\n")
            }
            append("$depth. ${sample.parent?.let { "Used ${it.mutation.name}" }
                ?: ""} ${sample.metrics?.show(kernel)} \n\n")
            append(sample.file)
        }
    }

    return show(sample, depth(sample))
}