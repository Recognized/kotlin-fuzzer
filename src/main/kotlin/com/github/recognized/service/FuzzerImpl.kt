package com.github.recognized.service

import com.github.recognized.Server
import com.github.recognized.dataset.Sample
import com.github.recognized.kodein
import org.kodein.di.generic.instance

class FuzzerImpl : Fuzzer {

    override suspend fun stat(): Statistics {
        return Server.stat()
    }

    override suspend fun pause() {
        Server.pause()
    }

    override suspend fun stop() {
        Server.stop()
    }

    override suspend fun start() {
        Server.start()
    }

    override suspend fun generation(offset: Int, count: Int, sortBy: SortOrder, onlyMutated: Boolean): List<Snippet> {
        val kernel by kodein.instance<Kernel>()
        return Server.generation().sortedBy(sortBy)
            .let {
                if (onlyMutated) {
                    it.filter { it.id?.startsWith("GN") == true }
                } else {
                    it
                }
            }
            .drop(offset).take(count).withIndex().map {
                Snippet(it.value.id!!, it.value.metrics!!, it.value.metrics!!.value(kernel))
            }
    }
}

fun List<Sample>.sortedBy(order: SortOrder): List<Sample> {
    val kernel by kodein.instance<Kernel>()
    return when (order) {
        SortOrder.Score -> sortedByDescending { it.metrics!!.value(kernel) }
        SortOrder.Analyze -> sortedByDescending { it.metrics!!.analyze.value }
        SortOrder.Generate -> sortedByDescending { it.metrics!!.generate.value }
        SortOrder.PsiElement -> sortedByDescending { it.metrics!!.psiElements }
        SortOrder.Symbols -> sortedByDescending { it.metrics!!.symbols }
        SortOrder.Name -> sortedWith((compareBy { it.id }))
    }
}