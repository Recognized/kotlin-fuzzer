package com.github.recognized.mutation

import com.github.recognized.dataset.AllCorpuses
import com.github.recognized.dataset.Sample
import com.github.recognized.random.Chooser
import com.github.recognized.random.shuffledSeq
import com.github.recognized.runtime.choose
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.js.translate.utils.PsiUtils
import kotlin.random.Random
import kotlin.reflect.KClass

class Add(val random: Random, corpus: AllCorpuses, val subtreeChooser: Chooser<PsiElement, PsiElement>) : Mutation {
    private val mayHaveChildren = mutableSetOf<KClass<*>>()

    init {
        corpus.samples().asSequence().mapNotNull { it.tree?.asSequence() }.flatMap { it }.forEach {
            if (it.children.isNotEmpty()) {
                mayHaveChildren += it::class
            }
        }
    }

    override val name: String = "Add"

    override fun mutate(corpus: List<Sample>, sample: Sample): String? {
        val tree = sample.tree?.copy() ?: error("Could not parse code")
        val source = subtreeChooser.choose(random, tree) {
            it::class in mayHaveChildren
        } ?: return null

        val addChildren = corpus.shuffledSeq(random).firstNotNull {
            val t = it.tree ?: return@firstNotNull null
            subtreeChooser.choose(random, t) {
                it.children.isNotEmpty() && it::class == source::class
            }
        }?.children ?: return null

        addChildren.forEach {
            source.add(it)
        }

        return tree.text
    }
}
