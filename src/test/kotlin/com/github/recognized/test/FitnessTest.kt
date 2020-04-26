package com.github.recognized.test

import com.github.recognized.compile.Analyzer
import com.github.recognized.compile.PsiFacade
import com.github.recognized.kodein
import com.github.recognized.metrics.FitnessFunction
import com.github.recognized.mutation.asSequence
import com.github.recognized.scoreAvg
import com.github.recognized.value
import org.jetbrains.kotlin.cfg.pseudocode.getContainingPseudocode
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtElementImpl
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
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
        val file =
            """

            fun main() {
                java.lang.System.out.println("Hello world!")
            }
        """.trimIndent()
        val facade = kodein.value<PsiFacade>()
        val psi = facade.getPsiExt(file)!!
        val code = psi.file.getContainingPseudocode(psi.context)!!
        code.instructions.forEach {
            println(it)
        }
    }
}
