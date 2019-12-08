package com.github.recognized.dataset

import com.github.recognized.CompileTarget
import com.github.recognized.compile.PsiFacade
import com.github.recognized.compile.hasErrorBelow
import com.github.recognized.runtime.disposing
import com.github.recognized.runtime.logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.list
import kotlinx.serialization.parse
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.instance
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Serializable
class CodeCount(val count: Int, val success: Boolean, val codes: List<Code>)

@Serializable
class Code(val code: String)

private val log = logger()

fun main() {

    val codeRegex = "\\{code(:.*?)?}(.*)\\{code}}".toRegex(RegexOption.DOT_MATCHES_ALL)
    val markdown = "```(\\w+)?(.*?)```".toRegex(RegexOption.DOT_MATCHES_ALL)

    val code = mutableListOf<String>()
    val dataDir = Paths.get("data", "youtrack")

    Files.newBufferedReader(dataDir.resolve("tickets.csv")).use {
        CSVParser(it, CSVFormat.DEFAULT).use { parser ->
            for (line in parser) {
                for (cell in line) {
                    codeRegex.findAll(cell).forEach {
                        code += it.groupValues[2]
                    }
                    markdown.findAll(cell).forEach {
                        code += it.groupValues[2]
                    }
                }
            }
        }
    }


    val separated = splitCanCompile(code)
    saveFilteredData(separated, dataDir)
}

fun splitCanCompile(data: Collection<String>): Map<Boolean, List<String>> {
    val log = logger { "SplitByCompile" }
    return disposing {
        val jvmFacade = PsiFacade(it, CompileTarget.Jvm)
        val jsFacade = PsiFacade(it, CompileTarget.Jvm)
        var index = 0
        data.groupBy {
            log.info { "Progress: ${index++}/${data.size}" }
            jvmFacade.getPsi(it)?.hasErrorBelow() == false || jsFacade.getPsi(it)?.hasErrorBelow() == false
        }
    }
}

fun saveFilteredData(data: Map<Boolean, List<String>>, dataDir: Path) {
    val failed = data[false].orEmpty().map(::Code).let { CodeCount(it.size, false, it) }
    val succeeded = data[true].orEmpty().map(::Code).let { CodeCount(it.size, true, it) }
    val json = Json(JsonConfiguration.Stable.copy(prettyPrint = true))
    Files.newBufferedWriter(dataDir.resolve("failed.json")).use {
        it.write(json.stringify(CodeCount.serializer(), failed))
    }
    Files.newBufferedWriter(dataDir.resolve("succeeded.json")).use {
        it.write(json.stringify(CodeCount.serializer(), succeeded))
    }
}

fun loadData(dataDir: Path): CodeCount {
    return Files.newBufferedReader(dataDir.resolve("succeeded.json")).use {
        Json(JsonConfiguration.Stable).parse(CodeCount.serializer(), it.readText())
    }
}

class YouTrackCorpus(facade: PsiFacade) : Corpus {
    private val data by lazy {
        loadData(Paths.get("data", "youtrack")).codes.withIndex()
            .map { IdSample("YT-${it.index}", LazySample(facade, it.value.code)) }
    }

    override fun samples(): List<Sample> = data
}
