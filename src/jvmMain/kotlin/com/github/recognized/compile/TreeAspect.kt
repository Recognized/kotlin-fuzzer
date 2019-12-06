package com.intellij.pom.tree

import com.intellij.pom.PomModel
import com.intellij.pom.PomModelAspect
import com.intellij.pom.event.PomModelEvent

class TreeAspect(model: PomModel) : PomModelAspect {
    fun update(event: PomModelEvent?) {}
}