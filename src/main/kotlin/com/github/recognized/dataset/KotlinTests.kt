package com.github.recognized.dataset

import com.github.recognized.runtime.disposing
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