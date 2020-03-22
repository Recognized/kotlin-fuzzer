package com.github.recognized.random

import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

abstract class Chooser<T : Any, R : Any> {

    fun choose(random: Random, x: T, constraint: (R) -> Boolean = TRUE): R? {
        return if (random.nextDouble() < failProbability()) {
            chooseImpl(random, x, TRUE)
        } else {
            chooseImpl(random, x, constraint)
        }
    }

    fun failProbability(): Double = 0.01

    abstract fun chooseImpl(random: Random, x: T, constraint: (R) -> Boolean = TRUE): R?

    companion object {
        val TRUE: (Any) -> Boolean = { true }
    }
}


// y = (exp(-lambda * x) - exp(-lambda * zeroX)) / (1 - exp(-lambda * zeroX))
fun reverseExpFn(lambda: Double): (y: Double) -> Double {
    assert(lambda > 0)
    val beta = exp(-lambda)
    val y0 = 1 - beta
    return {
        (ln(it * y0 + beta) / -lambda)
    }
}

fun expFn(lambda: Double): (x: Double) -> Double {
    val beta = exp(-lambda)
    return {
        (exp(-lambda * it) - beta) / (1 - beta)
    }
}
