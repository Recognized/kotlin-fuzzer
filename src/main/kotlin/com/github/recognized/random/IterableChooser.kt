package com.github.recognized.random

import com.github.recognized.runtime.choose
import kotlin.random.Random

open class IterableChooser<T : Any> : Chooser<Iterable<T>, T>() {

    override fun chooseImpl(random: Random, x: Iterable<T>, constraint: (T) -> Boolean): T? {
        return x.filter(constraint).choose(random)
    }
}