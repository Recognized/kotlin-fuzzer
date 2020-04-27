package com.github.recognized.compile

import com.github.recognized.*
import com.github.recognized.runtime.logger
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.ide.highlighter.FileTypeRegistrator
import com.intellij.ide.util.ProjectPropertiesComponentImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.PropertiesComponentImpl
import com.intellij.mock.MockEditorFactory
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.impl.FileTypeBean
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ModuleManagerComponent
import com.intellij.openapi.module.impl.ModuleManagerImpl
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.roots.ProjectRootModificationTrackerImpl
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.roots.impl.DirectoryIndexImpl
import com.intellij.openapi.roots.impl.ProjectFileIndexImpl
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.pom.PomModel
import com.intellij.pom.PomModelAspect
import com.intellij.pom.PomTransaction
import com.intellij.pom.core.impl.PomModelImpl
import com.intellij.pom.impl.PomTransactionBase
import com.intellij.pom.tree.TreeAspect
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.tree.TreeCopyHandler
import com.intellij.psi.util.PsiUtil
import com.intellij.testFramework.MockSchemeManagerFactory
import com.intellij.testFramework.registerServiceInstance
import org.jetbrains.kotlin.caches.resolve.IdePlatformKindResolution
import org.jetbrains.kotlin.caches.resolve.JvmPlatformKindResolution
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.caches.project.*
import org.jetbrains.kotlin.idea.caches.resolve.IdePackageOracleFactory
import org.jetbrains.kotlin.idea.caches.resolve.KotlinCacheServiceImpl
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettings
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.configuration.default
import org.jetbrains.kotlin.platform.DefaultIdeTargetPlatformKindProvider
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.kotlin.resolve.BindingContext
import java.io.File
import java.nio.file.spi.FileTypeDetector

private val log = logger("PsiFacade")

data class PsiResult(val file: KtFile, val context: BindingContext)

class PsiFacade(
    val parentDisposable: Disposable,
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
        env
    }

    fun getPsi(file: CharSequence): KtFile? {
        return (PsiFileFactory.getInstance(environment.project).createFileFromText(
            KotlinFileType.INSTANCE,
            "test",
            file,
            0,
            file.length
        ) as KtFile?)?.apply {
            forcedModuleInfo = SdkInfo(
                project,
                ProjectJdkImpl("1.8", JavaSdkImpl())
            )
        }
    }

    fun getPsiExt(text: CharSequence): PsiResult? {
        val (context, file) = Analyzer(parentDisposable).analyze(text.toString())
        return PsiResult(file, context)
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

val PsiElement.kt get() = this as? KtElement