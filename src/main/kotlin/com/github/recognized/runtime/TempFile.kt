package com.github.recognized.runtime

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.nio.file.Files

class TempFile(contents: String, parentDisposable: Disposable? = null) : Disposable {
    private val tempDir = Files.createTempDirectory("compile")
    private val tempFile = Files.createTempFile(tempDir, "temp", "kt_fuzzer").toAbsolutePath()
    val path: String get() = tempFile.toString()

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