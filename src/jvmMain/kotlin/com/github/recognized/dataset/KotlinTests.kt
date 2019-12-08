package com.github.recognized.dataset

import com.github.recognized.compile.PsiFacade
import com.github.recognized.runtime.disposing
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import java.nio.file.Paths

fun main() {
    val basePath = Paths.get("data", "kotlin-tests")

    val fileExt = "// *FILE: *\\w*?\\.java.*?((?=(// *FILE:))|((?!.)))".toRegex(
        setOf(
            RegexOption.DOT_MATCHES_ALL,
            RegexOption.MULTILINE
        )
    )


    val data = disposing {
        basePath.toFile().walkTopDown().mapNotNull {
            if (it.extension == "kt") {
                it.readText().replace(fileExt) { "" }
            } else {
                null
            }
        }
    }

    val separated = splitCanCompile(data.toList())
    saveFilteredData(separated, basePath)
}

class KotlinTestsCorpus(facade: PsiFacade) : Corpus {
    private val data by lazy {
        loadData(Paths.get("data", "kotlin-tests")).codes.withIndex()
            .map { IdSample("KT-tests-${it.index}", LazySample(facade, it.value.code)) }
    }

    override fun samples(): List<Sample> = data
}