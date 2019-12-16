package com.github.recognized.random

import com.github.recognized.mutation.asSequence
import com.github.recognized.mutation.firstNotNull
import com.intellij.psi.PsiElement
import kotlin.math.exp
import kotlin.random.Random

class SimpleSubtreeChooser(val lambda: Double) : Chooser<PsiElement, PsiElement> {

    override fun choose(random: Random, x: PsiElement, constraint: (PsiElement) -> Boolean): PsiElement? {
        return x.asSequence().toList().shuffled().firstNotNull { element ->
            val psiSize by lazy { element.asSequence().count() }
            val chance by lazy { random.nextDouble() > 1 - exp(-lambda * psiSize) }
            element.takeIf { constraint(it) && chance }
        }
    }
}