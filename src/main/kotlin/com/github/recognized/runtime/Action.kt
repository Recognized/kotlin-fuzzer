package com.github.recognized.runtime

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

inline fun <T> disposing(act: (Disposable) -> T): T {
    val dispose = Disposer.newDisposable()
    return try {
        act(dispose)
    } finally {
        Disposer.dispose(dispose)
    }
}