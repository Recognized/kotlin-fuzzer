package com.github.recognized.runtime

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.nio.file.Files
import java.nio.file.Path

class TempFile(contents: String, parentDisposable: Disposable? = null) : Disposable {
    private val tempDir = Files.createTempDirectory("compile")
    private val tempFile = Files.createFile(tempDir.resolve("Fuzzer.kt")).toAbsolutePath()
    val file: Path get() = tempFile
    val dir: Path get() = tempDir

    init {
        parentDisposable?.let {
            Disposer.register(it, this)
        }
        Files.newBufferedWriter(tempFile).use {
            it.write(contents)
        }
    }

    override fun dispose() {
        Files.delete(tempFile)
        Files.delete(tempDir)
    }
}