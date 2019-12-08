package com.github.recognized.mutation

import com.github.recognized.dataset.Corpus
import com.github.recognized.dataset.Sample
import org.jetbrains.kotlin.psi.KtElement
import org.kodein.di.Kodein
import org.kodein.di.generic.*

interface Mutation {
    val name: String get() = this::class.java.simpleName

    fun mutate(corpus: List<Sample>, tree: KtElement)
}

class AllMutations(val all: List<Mutation>)

fun Kodein.MainBuilder.mutations() {
    bind() from setBinding<Mutation>()

    bind<Mutation>().inSet() with singleton { Replace(instance()) }

    bind() from singleton { AllMutations(instance<Set<Mutation>>().toList()) }

}