package com.github.recognized.test

import com.github.recognized.dataset.AllCorpuses
import com.github.recognized.dataset.Sample
import com.github.recognized.kodein
import com.github.recognized.mutation.Add
import com.github.recognized.mutation.Mutation
import com.github.recognized.mutation.Replace
import org.junit.Test
import org.kodein.di.generic.instance
import kotlin.reflect.KClass

class MutationsTest {
    private val corpus by kodein.instance<AllCorpuses>()

    @Test
    fun `test replace`() {
        repeat(100) {
            println(mutate(Replace::class) {
                """
                import java.*
                
                fun main(args: Array<String>) {
                    println("hello world")
                }
            """.trimIndent()
            })
            println("\n-------------------\n")
        }
    }

    @Test
    fun `test add`() {
        repeat(100) {
            println(mutate(Add::class) {
                """
                import java.*
                
                fun main(args: Array<String>) {
                    println("hello world")
                }
            """.trimIndent()
            })
            println("\n-------------------\n")
        }
    }

    private fun <T : Mutation> mutate(mutationClass: KClass<T>, code: () -> String): String? {
        val mutations by kodein.instance<Set<Mutation>>()
        val sample = Sample(null, "test", code())
        return mutations.first {
            it::class == mutationClass
        }.mutate(corpus.samples(), sample)
    }
}