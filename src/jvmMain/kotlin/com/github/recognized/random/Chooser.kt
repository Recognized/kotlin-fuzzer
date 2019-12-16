package com.github.recognized.random

import kotlin.random.Random

interface Chooser<T : Any, R : Any> {
    fun choose(random: Random, x: T, constraint: (R) -> Boolean = { true }): R?
}