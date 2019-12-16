package com.github.recognized.compile

import com.github.recognized.CompileTarget
import com.github.recognized.Config
import com.github.recognized.configurationFiles
import com.github.recognized.runtime.logger
import com.intellij.lang.ASTNode
import com.intellij.mock.MockComponentManager
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.pom.PomModel
import com.intellij.pom.PomModelAspect
import com.intellij.pom.PomTransaction
import com.intellij.pom.impl.PomTransactionBase
import com.intellij.pom.tree.TreeAspect
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.tree.TreeCopyHandler
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.net.URLClassLoader

private val log = logger("PsiFacade")

class PsiFacade(
    parentDisposable: Disposable,
    target: CompileTarget,
    private val configuration: CompilerConfiguration.() -> Unit = {}
) {
    private val messageCollector = SimpleMessageCollector()

    private val environment by lazy {
        val cfg = CompilerConfiguration().also(configuration)
        if (target == CompileTarget.Jvm) {
            cfg.put(JVMConfigurationKeys.JDK_HOME, File(Config.JDK_HOME))
        }
        cfg.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
        val env = KotlinCoreEnvironment.createForTests(parentDisposable, cfg, target.configurationFiles)
        val project = env.project as MockProject
        Extensions.getArea(null).registerExtensionPoint(
            "com.intellij.treeCopyHandler",
            TreeCopyHandler::class.java.canonicalName,
            ExtensionPoint.Kind.INTERFACE
        )
        project.registerService(
            PomModel::class.java,
            object : UserDataHolderBase(), PomModel {
                override fun runTransaction(transaction: PomTransaction) {
                    (transaction as PomTransactionBase).run()
                }

                override fun <T : PomModelAspect> getModelAspect(p0: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return TreeAspect(this) as T
                }
            }
        )
        env
    }

    fun getPsi(file: CharSequence): KtFile? {
        return PsiFileFactory.getInstance(environment.project).createFileFromText(
            KotlinLanguage.INSTANCE,
            file
        ) as KtFile?
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

    fun others(): List<String> = others
    fun errors(): List<String> = errors

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