package com.github.recognized.mutation

import com.github.recognized.dataset.AllCorpuses
import com.github.recognized.dataset.Sample
import com.github.recognized.random.Chooser
import com.github.recognized.random.shuffledSeq
import com.github.recognized.runtime.Logger
import com.github.recognized.runtime.choose
import com.github.recognized.runtime.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.core.util.start
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.js.translate.utils.PsiUtils
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclarationUtil
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.astReplace
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getCall
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import kotlin.random.Random
import kotlin.reflect.KClass

private val log = logger("Add")

fun <T> Sequence<T>.withProgress(sequenceName: String, logger: Logger): Sequence<T> = withIndex().map {
    logger.info { "$sequenceName: ${it.index} / ... " }
    it.value
}

class Add(val random: Random, corpus: AllCorpuses, val subtreeChooser: Chooser<PsiElement, PsiElement>) : Mutation {
    private val mayHaveChildren = mutableSetOf<KClass<*>>()

    init {
        corpus.samples()
            .asSequence()
            .mapNotNull { it.tree?.file?.asSequence() }
            .flatMap { it }
            .withProgress("Add children", log)
            .forEach {
                if (it.children.isNotEmpty()) {
                    mayHaveChildren += it::class
                }
            }
    }

    override val name: String = "Add"

    override fun mutate(corpus: List<Sample>, sample: Sample): String? {
        val tree = sample.tree?.copy() ?: error("Could not parse code")
        val source = subtreeChooser.choose(random, tree.file) {
            it::class in mayHaveChildren
        } ?: return null

        val addChildren = corpus.shuffledSeq(random).firstNotNull {
            val t = it.tree ?: return@firstNotNull null
            subtreeChooser.choose(random, t.file) {
                it.children.isNotEmpty() && it::class == source::class
            }
        }?.children ?: return null

        addChildren.forEach {
            source.add(it)
        }

        return tree.file.text
    }
}