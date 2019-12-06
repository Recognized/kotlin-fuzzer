package com.github.recognized.runtime

import java.util.*


fun getClassesBfs(clazz: Class<*>): Set<Class<*>> {
    val classes = LinkedHashSet<Class<*>>()
    val nextLevel = LinkedHashSet<Class<*>>()
    nextLevel.add(clazz)
    do {
        classes.addAll(nextLevel)
        val thisLevel = LinkedHashSet(nextLevel)
        nextLevel.clear()
        for (each in thisLevel) {
            val superClass = each.superclass
            if (superClass != null && superClass != Any::class.java) {
                nextLevel.add(superClass)
            }
            for (eachInt in each.interfaces) {
                nextLevel.add(eachInt)
            }
        }
    } while (nextLevel.isNotEmpty())
    return classes
}

fun commonSuperClass(vararg classes: Class<*>): Set<Class<*>> { // start off with set from first hierarchy
    val rollingIntersect = LinkedHashSet(getClassesBfs(classes[0]))
    for (i in 1 until classes.size) {
        rollingIntersect.retainAll(getClassesBfs(classes[i]))
    }
    return rollingIntersect
}