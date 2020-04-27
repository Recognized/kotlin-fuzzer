package com.github.recognized.test

import com.github.recognized.Server
import com.github.recognized.compile.Analyzer
import com.github.recognized.compile.PsiFacade
import com.github.recognized.compile.hasErrorBelow
import com.github.recognized.compile.kt
import com.github.recognized.dataset.AllCorpuses
import com.github.recognized.dataset.Sample
import com.github.recognized.kodein
import com.github.recognized.metrics.FitnessFunction
import com.github.recognized.mutation.Replace
import com.github.recognized.mutation.asSequence
import com.github.recognized.runtime.logger
import com.github.recognized.scoreAvg
import com.github.recognized.value
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import org.jetbrains.kotlin.cfg.pseudocode.getContainingPseudocode
import org.jetbrains.kotlin.cfg.pseudocode.instructions.eval.ReadValueInstruction
import org.jetbrains.kotlin.cfg.pseudocode.instructions.special.VariableDeclarationInstruction
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.idea.util.toFuzzyType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoBefore
import org.jetbrains.kotlin.resolve.calls.callUtil.getCall
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
        val txt =
            """

            fun main() {
                java.lang.System.out.println("Hello world!" to 9)
                
                println(listOf("1", "2"))
                
                fun inner() {
                    println("nothing")
                }
            }
        """.trimIndent()
        val corpuses = kodein.value<AllCorpuses>()
//        Replace(kodein.value(), kodein.value()).mutate(corpuses.samples(), Sample())
    }
}
