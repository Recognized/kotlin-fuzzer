package com.github.recognized.random

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import kotlin.random.Random

class FineTunedSubtreeChooser(val chooser: Chooser<PsiElement, PsiElement>) : Chooser<PsiElement, PsiElement>() {

    override fun chooseImpl(random: Random, x: PsiElement, constraint: (PsiElement) -> Boolean): PsiElement? {
        return chooser.choose(random, x) { constraint(it) && meaningful(x, it) }
    }

    private fun meaningful(x: PsiElement, element: PsiElement): Boolean {
        return notInterestingClasses.none {
            it.isInstance(element)
        }
    }

    private val notInterestingClasses = listOf(
        KtConstantExpression::class,
        KtStringTemplateExpression::class,
        KtImportDirective::class
    )
}