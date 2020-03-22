package com.github.recognized.random

import com.github.recognized.mutation.asSequence
import com.github.recognized.mutation.firstNotNull
import com.intellij.psi.PsiElement
import kotlin.math.exp
import kotlin.random.Random

class SimpleSubtreeChooser(val lambda: Double) : Chooser<PsiElement, PsiElement>() {

    override fun chooseImpl(random: Random, x: PsiElement, constraint: (PsiElement) -> Boolean): PsiElement? {
        return x.asSequence().toList().shuffledSeq(random).firstNotNull { element ->
            val psiSize by lazy { element.asSequence().count() }
            val chance by lazy { random.nextDouble() > 1 - exp(-lambda * psiSize) }
            element.takeIf { constraint(it) && chance }
        }
    }
}


fun <T> List<T>.shuffledSeq(random: Random): Sequence<T> {
    val used = mutableSetOf<Int>()
    if (isEmpty()) return emptySequence()
    return sequence {
        repeat(size) {
            var next = random.nextInt(0, size)
            var tries = 1
            while (next in used && tries < 20) {
                tries++
                next = random.nextInt(0, size)
            }
            if (next in used) {
                (0 until size).filter { it !in used }.toMutableSet().shuffled().forEach {
                    yield(get(it))
                    return@sequence
                }
            } else {
                used += next
                yield(get(next))
            }
        }
    }
}