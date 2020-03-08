package com.github.recognized.mutation

import org.jetbrains.kotlin.psi.KtVisitor

class Identifier(val name: String, val type: String)

class ScopeContext(val identifier: List<Identifier>)

class ScopeVisitor : KtVisitor<>() {

}