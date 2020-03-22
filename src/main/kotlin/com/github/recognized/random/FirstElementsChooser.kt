package com.github.recognized.random

import kotlin.random.Random

class FirstElementsChooser<T : Any>(private val indexChooser: IndexChooser) : IterableChooser<T>() {

    override fun chooseImpl(random: Random, x: Iterable<T>, constraint: (T) -> Boolean): T? {
        val index = indexChooser.choose(random, x.count()) { constraint(x.elementAt(it)) }!!
        return x.elementAt(index)
    }
}