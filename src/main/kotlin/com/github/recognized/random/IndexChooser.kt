package com.github.recognized.random

import kotlin.math.floor
import kotlin.random.Random

class IndexChooser(val lambda: Double) : Chooser<Int, Int>() {

    override fun chooseImpl(random: Random, x: Int, constraint: (Int) -> Boolean): Int {
        val fn = reverseExpFn(lambda)
        val dice = random.nextDouble()

        var index = floor(fn(dice) * x).toInt()
        while (!constraint(index)) {
            index = floor(fn(dice) * x).toInt()
        }
        return index
    }
}