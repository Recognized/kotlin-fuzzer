package com.github.recognized.mutation

import com.github.recognized.dataset.Corpus
import com.github.recognized.dataset.Sample
import com.github.recognized.runtime.commonSuperClass
import com.github.recognized.runtime.logger
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtElementImplStub
import org.jetbrains.kotlin.psi.KtPureElement
import org.jetbrains.kotlin.psi.psiUtil.astReplace
import kotlin.random.Random

class Replace(val random: Random) : Mutation {
    private val log = logger()

    override fun mutate(corpus: List<Sample>, tree: KtElement) {
        val replacement = corpus.shuffled(random).firstNotNull { sample ->
            sample.tree?.asSequence()?.toList()?.shuffled(random)?.firstNotNull { element ->
                element.takeIf { swappable(it, tree) }
            }
        } as KtElement? ?: return
        log.info { "Replacing ${tree::class.simpleName}{${tree.text}} with ${replacement::class.simpleName}{${replacement.text}}" }
        tree.astReplace(replacement)
    }

    fun swappable(one: PsiElement, other: PsiElement): Boolean {
        val commonSuperClasses = commonSuperClass(one::class.java, other::class.java).filter {
            KtElement::class.java.isAssignableFrom(it)
                    && it != KtElement::class.java
                    && it != KtElementImplStub::class.java
                    && it != KtPureElement::class.java
        }
        if (commonSuperClasses.isNotEmpty()) {
            log.info { "Common super classes for ${one::class.simpleName} and ${other::class.simpleName} = $commonSuperClasses" }
        }
        return commonSuperClasses.isNotEmpty()
    }
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