package com.github.recognized.runtime

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Property<T>(value: T) {
    private val registrar = mutableSetOf<(T) -> Unit>()

    var value: T = value
        set(value) {
            val fire = field != value
            field = value
            if (fire) {
                registrar.forEach {
                    it(value)
                }
            }
        }


    fun forEach(disposable: Disposable, fn: (T) -> Unit) {
        registrar.add(fn)
        Disposer.register(disposable, Disposable {
            registrar.remove(fn)
        })
    }
}

suspend fun <T> Property<T>.await(value: T) {
    disposing { disposable ->
        suspendCoroutine<Unit> { cont ->
            forEach(disposable) {
                if (it == value) {
                    cont.resume(Unit)
                }
            }
        }
    }
}