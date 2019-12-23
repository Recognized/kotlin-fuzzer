package com.github.recognized.test

import com.github.recognized.random.IndexChooser
import com.github.recognized.random.expFn
import com.github.recognized.random.reverseExpFn
import org.junit.Test
import kotlin.random.Random

class RandomTest {

    @Test
    fun `test first index chooser`() {
        val stat = IntArray(100)
        val chooser = IndexChooser(3.0)
        val random = Random(0)
        repeat(1_000_000) {
            val ind = chooser.choose(random, stat.size)!!
            stat[ind] = stat[ind] + 1
        }
        println(stat.toList())
    }

    @Test
    fun `test exp fn`() {
        val fn = expFn(1.0)
        val reverse = reverseExpFn(1.0)
        println((0..999).map { fn(it.toDouble() / 1000.0) })
        println((0..999).map { reverse(it / 1000.0) })
    }
}