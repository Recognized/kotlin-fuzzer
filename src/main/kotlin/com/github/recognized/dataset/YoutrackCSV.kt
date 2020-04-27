package com.github.recognized.dataset

import com.github.recognized.CompileTarget
import com.github.recognized.compile.PsiFacade
import com.github.recognized.compile.hasErrorBelow
import com.github.recognized.kodein
import com.github.recognized.metrics.FitnessFunction
import com.github.recognized.parse
import com.github.recognized.runtime.disposing
import com.github.recognized.runtime.logger
import com.github.recognized.stringify
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.kodein.di.generic.instance
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CodeCount(val count: Int, val success: Boolean, val codes: List<Code>)

class Code(val code: String)

private val log = logger("YouTrack")

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
    val log = logger("SplitByCompile")
    return disposing {
        val jvmFacade = PsiFacade(it, CompileTarget.Jvm)
        val jsFacade = PsiFacade(it, CompileTarget.Jvm)
        var index = 0
        val fn by kodein.instance<FitnessFunction>()
        data.groupBy {
            log.info { "Progress: ${index++}/${data.size}" }
            (jvmFacade.getPsi(it)?.hasErrorBelow() == false || jsFacade.getPsi(it)?.hasErrorBelow() == false)
                    && fn.score(it) {}?.compiled == true
        }
    }
}

fun saveFilteredData(data: Map<Boolean, List<String>>, dataDir: Path, suffix: String = "") {
    val failed = data[false].orEmpty().map(::Code).let { CodeCount(it.size, false, it) }
    val succeeded = data[true].orEmpty().map(::Code).let { CodeCount(it.size, true, it) }
    Files.newBufferedWriter(dataDir.resolve("failed$suffix.json")).use {
        it.write(stringify(failed))
    }
    Files.newBufferedWriter(dataDir.resolve("succeeded$suffix.json")).use {
        it.write(stringify(succeeded))
    }
}

const val YOUTRACK_DIR = "data/youtrack"
const val KOTLIN_TESTS_DIR = "data/kotlin-tests"

fun loadData(dataDir: Path, suffix: String = ""): CodeCount {
    log.info { "Loading data: $dataDir" }
    return Files.newBufferedReader(dataDir.resolve("succeeded$suffix.json")).use {
        parse<CodeCount>(it.readText())
    }.also {
        log.info { "Finished loading: $dataDir" }
    }
}

class YouTrackCorpus(facade: PsiFacade) : Corpus {
    private val data by lazy {
        loadData(Paths.get("data", "youtrack")).codes.withIndex()
            .map { Sample(null, "YT-${it.index}", it.value.code) }
    }

    override fun samples(): List<Sample> = data
}
