package com.github.recognized.mutation

import com.github.recognized.dataset.Sample
import com.github.recognized.random.Chooser
import com.github.recognized.random.shuffledSeq
import com.github.recognized.runtime.commonSuperClass
import com.github.recognized.runtime.logger
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.cfg.pseudocode.getContainingPseudocode
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtElementImplStub
import org.jetbrains.kotlin.psi.KtPureElement
import org.jetbrains.kotlin.psi.psiUtil.astReplace
import kotlin.random.Random

private val log = logger("Replace")

class Replace(val random: Random, val subtreeChooser: Chooser<PsiElement, PsiElement>) : Mutation {

    override fun mutate(corpus: List<Sample>, sample: Sample): String? {
        val tree = sample.tree ?: error("Psi construction failed")
        val forReplace = subtreeChooser.choose(random, tree.file)!!
        val replacement = corpus.shuffledSeq(random).firstNotNull {
            val breed = it.tree ?: return@firstNotNull null
            subtreeChooser.choose(random, breed.file) {
                swappable(it, forReplace)
            }?.takeIf { it.parent != null }
        } as KtElement? ?: return null
        log.trace { "Replacing ${forReplace::class.simpleName}{${forReplace.text}} with ${replacement::class.simpleName}{${replacement.text}}" }
        forReplace.astReplace(replacement)
        return tree.file.text
    }
}

fun findInOut(parent: KtElement, child: KtElement) {
    val context = parent.analyze()
    val pseudocode = child.getContainingPseudocode(context)!!
}

//fun canSubstitute(fromContext: PsiElement, from: PsiElement, toContext: PsiElement, to: PsiElement): Boolean {
//    val fromInOut = findInOut(fromContext, from)
//    val toInOut = findInOut(toContext, to)
//}

fun swappable(one: PsiElement, other: PsiElement): Boolean {
    val commonSuperClasses = commonSuperClass(one::class.java, other::class.java).filter {
        KtElement::class.java.isAssignableFrom(it)
                && it != KtElement::class.java
                && it != KtElementImplStub::class.java
                && it != KtPureElement::class.java
    }
    if (commonSuperClasses.isNotEmpty()) {
        log.trace { "Common super classes for ${one::class.simpleName} and ${other::class.simpleName} = $commonSuperClasses" }
    }
    return commonSuperClasses.isNotEmpty()
}

fun <T, U> Iterable<T>.firstNotNull(fn: (T) -> U?) = asSequence().firstNotNull(fn)

fun <T, U> Sequence<T>.firstNotNull(fn: (T) -> U?): U? {
    for (elem in this) {
        val res = fn(elem)
        if (res != null) {
            return res
        }
    }
    return null
}

fun PsiElement.asSequence(): Sequence<PsiElement> = sequence {
    yield(this@asSequence)
    for (child in children) {
        yieldAll(child.asSequence())
    }
}

fun PsiElement.asLeafSequence(): Sequence<PsiElement> = sequence {
    if (children.isEmpty()) {
        yield(this@asLeafSequence)
    } else {
        for (child in children) {
            yieldAll(child.asLeafSequence())
        }
    }
}