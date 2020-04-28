package com.github.recognized.mutation

import com.github.recognized.compile.TypeContext
import com.github.recognized.compile.resolveText
import com.github.recognized.dataset.Sample
import com.github.recognized.random.Chooser
import com.github.recognized.random.shuffledSeq
import com.github.recognized.runtime.commonSuperClass
import com.github.recognized.runtime.logger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.core.util.end
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtElementImplStub
import org.jetbrains.kotlin.psi.KtPureElement
import org.jetbrains.kotlin.psi.psiUtil.astReplace
import kotlin.math.min
import kotlin.random.Random

private val log = logger("Replace")

class Replace(val random: Random, val subtreeChooser: Chooser<PsiElement, PsiElement>) : Mutation {

    override fun mutate(corpus: List<Sample>, sample: Sample): String? {
        val tree = sample.tree ?: error("Psi construction failed")
        val forReplace = subtreeChooser.choose(random, tree.file)!!
        val (replacement, s1) = corpus.shuffledSeq(random).firstNotNull {
            val breed = it.tree ?: return@firstNotNull null
            subtreeChooser.choose(random, breed.file) {
                swappable(it, forReplace)
            }?.takeIf { it.parent != null }?.let { x ->
                x to it
            }
        } ?: return null
        log.trace { "Replacing ${forReplace::class.simpleName}{${forReplace.text}} with ${replacement::class.simpleName}{${replacement.text}}" }
        val range = forReplace.textRange.startOffset until forReplace.textRange.endOffset
        val copy = tree.file.text.replaceRange(range, replacement.text)
        log.info { "Replace ${forReplace.text} -> ${replacement.text}" }
        val (newFile, newContext) = resolveText(copy) ?: error("Failed resolve new text")
        val replacementMirror = findForSamePath(newFile, tree.file, forReplace) ?: error("Couldn't find replacement mirror")
        return renameUnresolved(copy, replacementMirror, newContext, s1.tree!!.context)
    }
}

fun findForSamePath(source: PsiElement, mirror: PsiElement, find: PsiElement): PsiElement? {
    if (mirror == find) {
        return source
    }
    for (i in 0 until min(mirror.children.size, source.children.size)) {
        val found = findForSamePath(source.children[i], mirror.children[i], find)
        if (found != null) {
            return found
        }
    }
    return null
}

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