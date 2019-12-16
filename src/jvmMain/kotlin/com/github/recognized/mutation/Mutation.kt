package com.github.recognized.mutation

import com.github.recognized.dataset.Sample
import com.github.recognized.random.Chooser
import com.github.recognized.random.SimpleSubtreeChooser
import com.intellij.psi.PsiElement
import org.kodein.di.Kodein
import org.kodein.di.generic.*

interface Mutation {
    val name: String get() = this::class.java.simpleName

    fun mutate(corpus: List<Sample>, sample: Sample): String?
}

class AllMutations(val all: List<Mutation>)

fun Kodein.MainBuilder.mutations() {
    bind() from setBinding<Mutation>()
    bind<Chooser<PsiElement, PsiElement>>() with singleton { SimpleSubtreeChooser(0.2) }

    bind<Mutation>().inSet() with singleton { Replace(instance(), instance()) }
    bind<Mutation>().inSet() with singleton { Add(instance(), instance(), instance()) }

    bind() from singleton { AllMutations(instance<Set<Mutation>>().toList()) }

}