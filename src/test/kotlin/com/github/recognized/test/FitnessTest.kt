package com.github.recognized.test

import com.github.recognized.compile.PsiFacade
import com.github.recognized.kodein
import com.github.recognized.metrics.FitnessFunction
import com.github.recognized.scoreAvg
import org.jetbrains.kotlin.cfg.pseudocode.getContainingPseudocode
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.junit.Test
import org.kodein.di.generic.instance

class FitnessTest {

    @Test
    fun test() {
        val fitness by kodein.instance<FitnessFunction>()
        val codeSample = """
            import Test.*
            
            enum class Test {
                A
            }
            
            fun main() {
                val x = A::class
                println(x)
            }
        """.trimIndent()
        val codeTest = """
            import Test.*

            enum class Test {
                String
            }
            
            fun main() {
                val x = String::class
                println(x)
            }
            
        """.trimIndent()

        // warm up
        fitness.scoreAvg(codeSample, repeatCount = 10)
        fitness.scoreAvg(codeTest, repeatCount = 10)

        println(fitness.scoreAvg(codeSample, repeatCount = 10))
        println(fitness.scoreAvg(codeTest, repeatCount = 10))
    }

    @Test
    fun `test binding`() {
        val facade by kodein.instance<PsiFacade>()
        val file = facade.getPsi(
            """
            import Test.*
                        
            enum class Test {
                A
            }
            
            fun main() {
                val x = A::class
                println(x)
            }
        """.trimIndent()
        )!!
        val ctx = file.analyze()
        file.getContainingPseudocode(ctx)!!.instructions.forEach {
            println(it)
        }
    }
}
