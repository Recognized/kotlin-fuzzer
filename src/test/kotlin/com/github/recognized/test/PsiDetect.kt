package com.github.recognized.test

import com.github.recognized.compile.PsiFacade
import com.github.recognized.kodein
import com.github.recognized.mutation.asSequence
import com.intellij.psi.PsiElement
import org.junit.Test
import org.kodein.di.generic.instance

class PsiDetect {

    @Test
    fun `test get classname`() {
        val facade by kodein.instance<PsiFacade>()
        facade.getPsi(
            """
                import java.lang.*
                
            fun main() {
                val x = "ss"
                println(9)
            }
        """.trimIndent()
        )!!.showTree()
    }

    private fun PsiElement.showTree() {
        asSequence().forEach {
            println("${it::class.simpleName}: ${it.text}")
            println("------")
        }
    }
}