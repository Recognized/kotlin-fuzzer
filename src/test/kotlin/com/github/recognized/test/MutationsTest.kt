package com.github.recognized.test

import com.github.recognized.compile.TypeContext
import com.github.recognized.dataset.AllCorpuses
import com.github.recognized.dataset.Sample
import com.github.recognized.kodein
import com.github.recognized.mutation.Add
import com.github.recognized.mutation.Mutation
import com.github.recognized.mutation.Replace
import com.github.recognized.runtime.logger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import org.junit.Test
import org.kodein.di.generic.instance
import javax.swing.SwingUtilities
import kotlin.reflect.KClass

class MutationsTest {
    private val corpus by kodein.instance<AllCorpuses>()
    private val log = logger("Test")

    @Test
    fun `test replace`() {
        TypeContext.runInEdt {
            WriteCommandAction.runWriteCommandAction(TypeContext.project) {
                repeat(100) {
                    try {

                        println(mutate(Replace::class) {
                            """
                import java.*
                
                fun main(args: Array<String>) {
                    println("hello world")
                }
            """.trimIndent()
                        })
                        println("\n-------------------\n")
                    } catch (ex: Throwable) {
                        log.error(ex)
                    }
                }
            }
        }
        Thread.sleep(200000)
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