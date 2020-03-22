package com.github.recognized.runtime

import com.github.recognized.mutation.asSequence
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import kotlin.random.Random

inline fun <T> disposing(act: (Disposable) -> T): T {
    val dispose = Disposer.newDisposable()
    return try {
        act(dispose)
    } finally {
        Disposer.dispose(dispose)
    }
}

fun <T> List<T>.choose(random: Random): T {
    assert(isNotEmpty())
    return get(random.nextInt(0, size))
}

fun PsiElement.choose(random: Random): PsiElement {
    return asSequence().toList().choose(random)
}