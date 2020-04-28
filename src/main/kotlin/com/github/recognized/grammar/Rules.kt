/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.github.recognized.grammar

import org.antlr.parser.antlr4.ANTLRv4Lexer
import org.antlr.parser.antlr4.ANTLRv4Parser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

object Rules {

    fun getRules(file: String) {

    }

    fun parseKotlinTree(file: String) {
        val stream = CharStreams.fromString(file)
        val lexer = ANTLRv4Lexer(stream)
        val tokenStream = CommonTokenStream(lexer)
        val parser = ANTLRv4Parser(tokenStream)
    }
}