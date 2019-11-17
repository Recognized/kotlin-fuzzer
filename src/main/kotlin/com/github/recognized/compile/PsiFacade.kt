package com.github.recognized.compile

import com.github.recognized.CompileTarget
import com.github.recognized.Config
import com.github.recognized.configurationFiles
import com.github.recognized.runtime.logger
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.JavaCoreProjectEnvironment
import com.intellij.openapi.Disposable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.GroupingMessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

private val log = logger()

class PsiFacade(
    parentDisposable: Disposable,
    target: CompileTarget,
    private val configuration: CompilerConfiguration.() -> Unit = {}
) {
    val messageCollector = SimpleMessageCollector()
    private val environment by lazy {
        val cfg = CompilerConfiguration().also(configuration)
        if (target == CompileTarget.Jvm) {
            cfg.put(JVMConfigurationKeys.JDK_HOME, File(Config.JDK_HOME))
        }
        cfg.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
        KotlinCoreEnvironment.createForProduction(parentDisposable, cfg, target.configurationFiles)
    }

    fun getPsi(file: CharSequence): KtFile {
        return PsiFileFactory.getInstance(environment.project).createFileFromText(
            KotlinLanguage.INSTANCE,
            file
        ) as KtFile
    }
}

fun PsiElement.hasErrorBelow(): Boolean {
    return PsiUtil.hasErrorElementChild(this) || children.any {
        it.hasErrorBelow()
    }
}

class SimpleMessageCollector : MessageCollector {
    private val errors = mutableListOf<String>()
    private val others = mutableListOf<String>()

    override fun clear() {
        errors.clear()
        others.clear()
    }

    override fun hasErrors(): Boolean {
        return errors.isNotEmpty()
    }

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
        if (severity.isError) {
            errors += message
        } else {
            others += message
        }
    }
}