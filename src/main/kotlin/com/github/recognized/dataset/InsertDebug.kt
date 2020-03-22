package com.github.recognized.dataset

import com.github.recognized.compile.PsiFacade
import com.github.recognized.kodein
import com.github.recognized.mutation.asSequence
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.kodein.di.generic.instance
import java.nio.file.Paths

fun main() {
    forEach(KOTLIN_TESTS_DIR, YOUTRACK_DIR) { path ->
        val facade by kodein.instance<PsiFacade>()
        val data = loadData(Paths.get(path))
        val mappedCodes = data.codes.map {
            val tree = facade.getPsi(it.code)!!
            var nextText = it.code
            var accum = 0
            val definitions = mutableSetOf<String>()
            tree.asSequence().forEach {
                if (it is KtProperty) {
                    definitions += it.name!!
                }
            }
            tree.asSequence().forEach { element ->
                if (element is KtNameReferenceExpression && element.text in definitions) {
                    val replacement = wrapVar(element.text)
                    nextText = nextText.replaceRange(
                        element.textRange.startOffset + accum until element.textRange.endOffset + accum,
                        replacement
                    )
                    accum += replacement.length - element.textLength
                }
            }
            nextText
        }
        saveFilteredData(mapOf(true to mappedCodes), Paths.get(path), "_with_debug")
    }
}

fun PsiElement.visit(visitor: (PsiElement) -> Boolean) {
    if (visitor(this)) {
        children.forEach {
            it.visit(visitor)
        }
    }
}

private fun wrapVar(str: String): String {
    return "<!DEBUG_INFO_EXPRESSION_TYPE(\"\")!>$str<!>"
}

fun <T> forEach(vararg args: T, consume: (T) -> Unit) {
    args.forEach(consume)
}