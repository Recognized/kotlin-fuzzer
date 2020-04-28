/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package com.github.recognized.compile

import com.github.recognized.runtime.logger
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.ide.highlighter.FileTypeRegistrator
import com.intellij.ide.util.ProjectPropertiesComponentImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.mock.MockEditorFactory
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.ExtensionsArea
import com.intellij.openapi.fileTypes.*
import com.intellij.openapi.fileTypes.impl.FileTypeBean
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ModuleManagerComponent
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.roots.ProjectRootModificationTrackerImpl
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.roots.impl.DirectoryIndexImpl
import com.intellij.openapi.roots.impl.ProjectFileIndexImpl
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.PomModel
import com.intellij.pom.core.impl.PomModelImpl
import com.intellij.pom.tree.TreeAspect
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.TreeCopyHandler
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.MockSchemeManagerFactory
import junit.framework.AssertionFailedError
import org.assertj.core.util.Files
import org.jetbrains.kotlin.TestsCompilerError
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.analyzer.common.CommonResolverForModuleFactory
import org.jetbrains.kotlin.builtins.jvm.JvmBuiltIns
import org.jetbrains.kotlin.caches.resolve.IdePlatformKindResolution
import org.jetbrains.kotlin.caches.resolve.JvmPlatformKindResolution
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.checkers.BaseDiagnosticsTest
import org.jetbrains.kotlin.checkers.CompilerTestLanguageVersionSettings
import org.jetbrains.kotlin.checkers.LazyOperationsLog
import org.jetbrains.kotlin.checkers.LoggingStorageManager
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.codegen.forTestCompile.ForTestCompileRuntime
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.context.SimpleGlobalContext
import org.jetbrains.kotlin.context.withModule
import org.jetbrains.kotlin.context.withProject
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.DiagnosticUtils
import org.jetbrains.kotlin.diagnostics.Errors.*
import org.jetbrains.kotlin.frontend.java.di.createContainerForLazyResolveWithJava
import org.jetbrains.kotlin.frontend.java.di.initJvmBuiltInsForTopDownAnalysis
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.caches.resolve.KotlinCacheServiceImpl
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.configuration.default
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.load.java.lazy.SingleModuleClassResolver
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.platform.DefaultIdeTargetPlatformKindProvider
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.calls.model.MutableResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.jvm.JavaDescriptorResolver
import org.jetbrains.kotlin.resolve.lazy.KotlinCodeAnalyzer
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.serialization.deserialization.MetadataPartProvider
import org.jetbrains.kotlin.storage.ExceptionTracker
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.test.*
import org.jetbrains.kotlin.test.util.DescriptorValidator
import org.jetbrains.kotlin.test.util.RecursiveDescriptorComparator
import org.jetbrains.kotlin.test.util.RecursiveDescriptorComparator.RECURSIVE
import org.jetbrains.kotlin.test.util.RecursiveDescriptorComparator.RECURSIVE_ALL
import org.jetbrains.kotlin.utils.keysToMap
import org.junit.Assert
import java.io.File
import java.util.*
import java.util.function.Predicate
import java.util.regex.Pattern

object GlobalRegistrations {
    init {
        Extensions.getRootArea().registerIfNotExists(
            TreeCopyHandler.EP_NAME.name,
            TreeCopyHandler::class.java,
            ExtensionPoint.Kind.INTERFACE
        )
        Extensions.getRootArea().registerIfNotExists(
            "com.intellij.fileType",
            FileTypeBean::class.java,
            ExtensionPoint.Kind.BEAN_CLASS
        )
        Extensions.getRootArea().registerIfNotExists(
            FileTypeFactory.FILE_TYPE_FACTORY_EP.name,
            FileTypeFactory::class.java,
            ExtensionPoint.Kind.BEAN_CLASS
        )
        Extensions.getRootArea().registerIfNotExists(
            FileTypeRegistrator.EP_NAME.name,
            FileTypeRegistrator::class.java,
            ExtensionPoint.Kind.INTERFACE
        )
        Extensions.getRootArea().registerIfNotExists(
            FileTypeRegistry.FileTypeDetector.EP_NAME.name,
            FileTypeRegistry.FileTypeDetector::class.java,
            ExtensionPoint.Kind.INTERFACE
        )
        Extensions.getRootArea().registerIfNotExists(
            FileTypeOverrider.EP_NAME.name,
            FileTypeOverrider::class.java,
            ExtensionPoint.Kind.INTERFACE
        )
        Extensions.getRootArea().registerIfNotExists(
            "com.intellij.openapi.fileTypes.FileTypeManager",
            FileTypeManagerImpl::class.java,
            ExtensionPoint.Kind.BEAN_CLASS
        )
        Extensions.getRootArea().registerIfNotExists(
            "org.jetbrains.kotlin.idePlatformKind",
            IdePlatformKind::class.java,
            ExtensionPoint.Kind.BEAN_CLASS
        )
        Extensions.getRootArea().registerIfNotExists(
            IdePlatformKindResolution.extensionPointName.name,
            IdePlatformKindResolution::class.java,
            ExtensionPoint.Kind.INTERFACE
        )
        IdePlatformKindResolution.registerExtension(JvmPlatformKindResolution())
        Extensions.getRootArea().getExtensionPoint<JvmIdePlatformKind>("org.jetbrains.kotlin.idePlatformKind")
            .registerExtension(JvmIdePlatformKind)
    }

    private fun <T : Any> ExtensionsArea.registerIfNotExists(name: String, klass: Class<T>, kind: ExtensionPoint.Kind) {
        val service = try {
            getExtensionPoint<T>(name)
        } catch (ex: Throwable) {
            null
        }
        if (service != null) {
            println("EP $klass is already registered")
        } else {
            registerExtensionPoint(name, klass.canonicalName, kind)
        }
    }

    val fileTypeManager by lazy { FileTypeManagerImpl() }
    val mockSchemeManager by lazy { MockSchemeManagerFactory() }
}

fun resolveText(text: String): PsiResult? {
    val disposable = Disposer.newDisposable()
    val (context, file) = TypeContext.analyze(text, disposable)
    return PsiResult(file, context, disposable)
}

private val log = logger("TypeContext")

object TypeContext : BaseDiagnosticsTest() {
    override fun analyzeAndCheck(testDataFile: File, files: List<TestFile>) {
        try {
            analyzeAndCheckUnhandled(testDataFile, files)
        } catch (t: AssertionError) {
            throw t
        } catch (t: AssertionFailedError) {
            throw t
        } catch (t: Throwable) {
            throw TestsCompilerError(t)
        }
    }

    private val testPath = Files.newTemporaryFolder()
    private val kotlinSourceRoot = KotlinTestUtils.tmpDir("kotlin-src")

    init {
        Disposer.register(testRootDisposable, Disposable { testPath.deleteRecursively() })
        setUp()
        environment = createEnvironment()
        setupEnvironment(environment)
    }

    override val project: MockProject = environment.project as MockProject

    override fun getTestJdkKind(files: List<TestFile>): TestJdkKind {
        return TestJdkKind.FULL_JDK
    }

    fun runInEdt(fn: () -> Unit) {
        ApplicationManager.getApplication().invokeAndWait {
            try {
                fn()
            } catch (ex: Throwable) {
                log.error(ex)
            }
        }
    }

    fun analyze(text: String, disposable: Disposable): Pair<BindingContext, KtFile> {
        val file = File.createTempFile("test", "_.kt", testPath)
        file.bufferedWriter().use { it.write(text) }
        var expectedText = KotlinTestUtils.doLoadFile(file)
        if (coroutinesPackage.isNotEmpty()) {
            expectedText = expectedText.replace("COROUTINES_PACKAGE", coroutinesPackage)
        }
        val files = createTestFilesFromFile(file, expectedText)

        return analyzeAndCheckUnhandled(file, files).first().let { it.context to it.files.first() }
    }

    private fun createEnvironment(): KotlinCoreEnvironment {
        val classpath: MutableList<File> = ArrayList()
        classpath.add(KotlinTestUtils.getAnnotationsJar())
        classpath.addAll(getExtraClasspath())
        classpath.add(ForTestCompileRuntime.runtimeJarForTestsWithJdk8())
        classpath.add(ForTestCompileRuntime.coroutinesCompatForTests())
        val configuration = KotlinTestUtils.newConfiguration(
            ConfigurationKind.ALL,
            TestJdkKind.FULL_JDK,
            classpath,
            emptyList()
        )
        configuration.addKotlinSourceRoot(kotlinSourceRoot.path)

        // Currently, we're testing IDE behavior when generating the .txt files for comparison, but this can be changed.
        // The main difference is the fact that the new class file reading implementation doesn't load parameter names from JDK classes.
//        configuration.put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)

        return createForTests(testRootDisposable, configuration, getEnvironmentConfigFiles())
    }

    override fun setupEnvironment(environment: KotlinCoreEnvironment) {
        val project = environment.project as MockProject
        GlobalRegistrations

        environment.projectEnvironment.environment.registerIfNotExists(
            PropertiesComponent::class.java,
            ProjectPropertiesComponentImpl()
        )
        environment.projectEnvironment.environment.registerIfNotExists(
            EditorFactory::class.java,
            MockEditorFactory()
        )
        environment.projectEnvironment.environment.registerIfNotExists(
            DefaultIdeTargetPlatformKindProvider::class.java,
            object : DefaultIdeTargetPlatformKindProvider {
                override val defaultPlatform: TargetPlatform
                    get() = JvmIdePlatformKind.defaultPlatform
            }
        )
        environment.projectEnvironment.environment.registerIfNotExists(
            SchemeManagerFactory::class.java,
            GlobalRegistrations.mockSchemeManager
        )
        environment.projectEnvironment.environment.registerIfNotExists(FileTypeManager::class.java, GlobalRegistrations.fileTypeManager)
        project.registerIfNotExists(ProjectRootManager::class.java, ProjectRootManagerImpl(project))
        project.registerIfNotExists(DirectoryIndex::class.java, DirectoryIndexImpl(project))
        project.registerIfNotExists(ProjectFileIndex::class.java, ProjectFileIndexImpl(project))
        project.registerIfNotExists(LibraryModificationTracker::class.java, LibraryModificationTracker(project))
        project.registerIfNotExists(ScriptDependenciesModificationTracker::class.java, ScriptDependenciesModificationTracker())
        project.registerIfNotExists(ProjectRootModificationTracker::class.java, ProjectRootModificationTrackerImpl(project))
        project.registerIfNotExists(PomModel::class.java, PomModelImpl(project).also { TreeAspect(it) })
        project.registerIfNotExists(ScriptConfigurationManager::class.java, ScriptConfigurationManager.default(project))
        project.registerIfNotExists(KotlinCacheService::class.java, KotlinCacheServiceImpl(project))
//        project.registerIfNotExists(KotlinCommonCompilerArgumentsHolder::class.java, KotlinCommonCompilerArgumentsHolder(project))
//        project.registerIfNotExists(PropertiesComponent::class.java, ProjectPropertiesComponentImpl())
//        project.registerIfNotExists(KotlinCompilerSettings::class.java, KotlinCompilerSettings(project))
        project.registerIfNotExists(ModuleManager::class.java, ModuleManagerComponent(project))
//        project.registerIfNotExists(IdePackageOracleFactory::class.java, IdePackageOracleFactory(project))
    }

    private fun <T : Any> CoreApplicationEnvironment.registerIfNotExists(klass: Class<T>, instance: T) {
        val service = try {
            application.getService(klass, false)
        } catch (ex: Throwable) {
            null
        }
        if (service != null) {
            println("Class $klass is already registered")
        } else {
            registerApplicationService(klass, instance)
        }
    }

    private fun <T : Any> MockProject.registerIfNotExists(klass: Class<T>, instance: T) {
        val service = try {
            getService(klass, false)
        } catch (ex: Throwable) {
            null
        }
        if (service != null) {
            println("Class $klass is already registered")
        } else {
            registerService(klass, instance)
        }
    }

    class RResult(val context: BindingContext, val files: List<KtFile>)

    override fun isJavaSourceRootNeeded(): Boolean {
        return true
    }

    override fun isKotlinSourceRootNeeded(): Boolean {
        return true
    }

    private fun analyzeAndCheckUnhandled(testDataFile: File, files: List<TestFile>): List<RResult> {
        val groupedByModule = files.groupBy(TestFile::module)

        var lazyOperationsLog: LazyOperationsLog? = null

        val tracker = ExceptionTracker()
        val storageManager: StorageManager
        if (files.any(TestFile::checkLazyLog)) {
            lazyOperationsLog = LazyOperationsLog(HASH_SANITIZER)
            storageManager = LoggingStorageManager(
                LockBasedStorageManager.createWithExceptionHandling("AbstractDiagnosticTest", tracker),
                lazyOperationsLog.addRecordFunction
            )
        } else {
            storageManager = LockBasedStorageManager.createWithExceptionHandling("AbstractDiagnosticTest", tracker)
        }

        val context = SimpleGlobalContext(storageManager, tracker)

        val modules = createModules(groupedByModule, context.storageManager)
        val moduleBindings = HashMap<TestModule?, BindingContext>()

        val languageVersionSettingsByModule = HashMap<TestModule?, LanguageVersionSettings>()

        val res = mutableListOf<RResult>()

        for ((testModule, testFilesInModule) in groupedByModule) {
            val ktFiles = getKtFiles(testFilesInModule, true)

            val oldModule = modules[testModule]!!

            val languageVersionSettings = CompilerTestLanguageVersionSettings(
                mapOf(
                    LanguageFeature.Coroutines to LanguageFeature.State.ENABLED,
                    LanguageFeature.NewInference to LanguageFeature.State.DISABLED
                ),
                ApiVersion.KOTLIN_1_3,
                LanguageVersion.KOTLIN_1_3
            )

            languageVersionSettingsByModule[testModule] = languageVersionSettings

            val moduleContext = context.withProject(project).withModule(oldModule)

            val separateModules = groupedByModule.size == 1 && groupedByModule.keys.single() == null
            val result = analyzeModuleContents(
                moduleContext, ktFiles, NoScopeRecordCliBindingTrace(),
                languageVersionSettings, separateModules, loadJvmTarget(testFilesInModule)
            )
            if (oldModule != result.moduleDescriptor) {
                // For common modules, we use DefaultAnalyzerFacade who creates ModuleDescriptor instances by itself
                // (its API does not support working with a module created beforehand).
                // So, we should replace the old (effectively discarded) module with the new one everywhere in dependencies.
                // TODO: dirty hack, refactor this test so that it doesn't create ModuleDescriptor instances
                modules[testModule] = result.moduleDescriptor as ModuleDescriptorImpl
                for (module in modules.values) {
                    @Suppress("DEPRECATION")
                    val it = (module.testOnly_AllDependentModules as MutableList).listIterator()
                    while (it.hasNext()) {
                        if (it.next() == oldModule) {
                            it.set(result.moduleDescriptor as ModuleDescriptorImpl)
                        }
                    }
                }
            }

            moduleBindings[testModule] = result.bindingContext
            checkAllResolvedCallsAreCompleted(ktFiles, result.bindingContext, languageVersionSettings)

            res.add(RResult(result.bindingContext, ktFiles))
        }
        return res
    }

    open fun loadJvmTarget(module: List<TestFile>): JvmTarget {
        var result: JvmTarget? = null
        for (file in module) {
            val current = file.jvmTarget
            if (current != null) {
                if (result != null && result != current) {
                    Assert.fail(
                        "This is not supported. Please move all directives into one file"
                    )
                }
                result = current
            }
        }

        return result ?: JvmTarget.DEFAULT
    }

    private fun checkDynamicCallDescriptors(expectedFile: File, testFiles: List<TestFile>) {
        val serializer = RecursiveDescriptorComparator(RECURSIVE_ALL)

        val actualText = StringBuilder()

        for (testFile in testFiles) {
            for (descriptor in testFile.dynamicCallDescriptors) {
                val actualSerialized = serializer.serializeRecursively(descriptor)
                actualText.append(actualSerialized)
            }
        }

        if (actualText.isNotEmpty() || expectedFile.exists()) {
            KotlinTestUtils.assertEqualsToFile(expectedFile, actualText.toString())
        }
    }

    open fun shouldSkipJvmSignatureDiagnostics(groupedByModule: Map<TestModule?, List<TestFile>>): Boolean =
        groupedByModule.size > 1

    private fun checkLazyResolveLog(lazyOperationsLog: LazyOperationsLog, testDataFile: File): Throwable? =
        try {
            val expectedFile = getLazyLogFile(testDataFile)
            KotlinTestUtils.assertEqualsToFile(expectedFile, lazyOperationsLog.getText(), HASH_SANITIZER)
            null
        } catch (e: Throwable) {
            e
        }

    private fun getLazyLogFile(testDataFile: File): File =
        File(FileUtil.getNameWithoutExtension(testDataFile.absolutePath) + ".lazy.log")

    fun analyzeModuleContents(
        moduleContext: ModuleContext,
        files: List<KtFile>,
        moduleTrace: BindingTrace,
        languageVersionSettings: LanguageVersionSettings,
        separateModules: Boolean,
        jvmTarget: JvmTarget
    ): AnalysisResult {
        @Suppress("NAME_SHADOWING")
        var files = files

        // New JavaDescriptorResolver is created for each module, which is good because it emulates different Java libraries for each module,
        // albeit with same class names
        // See TopDownAnalyzerFacadeForJVM#analyzeFilesWithJavaIntegration

        // Temporary solution: only use separate module mode in single-module tests because analyzeFilesWithJavaIntegration
        // only supports creating two modules, whereas there can be more than two in multi-module diagnostic tests
        // TODO: always use separate module mode, once analyzeFilesWithJavaIntegration can create multiple modules
        if (separateModules) {
            return TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                moduleContext.project,
                files,
                moduleTrace,
                environment.configuration.copy().apply {
                    this.languageVersionSettings = languageVersionSettings
                    this.put(JVMConfigurationKeys.JVM_TARGET, jvmTarget)
                },
                environment::createPackagePartProvider
            )
        }

        val moduleDescriptor = moduleContext.module as ModuleDescriptorImpl

        val platform = moduleDescriptor.platform
        if (platform.isCommon()) {
            return CommonResolverForModuleFactory.analyzeFiles(
                files, moduleDescriptor.name, true, languageVersionSettings,
                mapOf(
                    MODULE_FILES to files
                )
            ) { _ ->
                // TODO
                MetadataPartProvider.Empty
            }
        } else if (platform != null) {
            // TODO: analyze with the correct platform, not always JVM
            files += getCommonCodeFilesForPlatformSpecificModule(moduleDescriptor)
        }

        val moduleContentScope = GlobalSearchScope.allScope(moduleContext.project)
        val moduleClassResolver = SingleModuleClassResolver()

        val container = createContainerForLazyResolveWithJava(
            JvmPlatforms.jvmPlatformByTargetVersion(jvmTarget), // TODO(dsavvinov): do not pass JvmTarget around
            moduleContext,
            moduleTrace,
            FileBasedDeclarationProviderFactory(moduleContext.storageManager, files),
            moduleContentScope,
            moduleClassResolver,
            CompilerEnvironment, LookupTracker.DO_NOTHING,
            ExpectActualTracker.DoNothing,
            environment.createPackagePartProvider(moduleContentScope),
            languageVersionSettings,
            useBuiltInsProvider = true
        )

        container.initJvmBuiltInsForTopDownAnalysis()
        moduleClassResolver.resolver = container.get<JavaDescriptorResolver>()

        moduleDescriptor.initialize(
            CompositePackageFragmentProvider(
                listOf(
                    container.get<KotlinCodeAnalyzer>().packageFragmentProvider,
                    container.get<JavaDescriptorResolver>().packageFragmentProvider
                )
            )
        )

        container.get<LazyTopDownAnalyzer>().analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, files)

        return AnalysisResult.success(moduleTrace.bindingContext, moduleDescriptor)
    }

    private fun getCommonCodeFilesForPlatformSpecificModule(moduleDescriptor: ModuleDescriptorImpl): List<KtFile> {
        // We assume that a platform-specific module _implements_ all declarations from common modules which are immediate dependencies.
        // So we collect all sources from such modules to analyze in the platform-specific module as well
        @Suppress("DEPRECATION")
        val dependencies = moduleDescriptor.testOnly_AllDependentModules

        // TODO: diagnostics on common code reported during the platform module analysis should be distinguished somehow
        // E.g. "<!JVM:ACTUAL_WITHOUT_EXPECT!>...<!>
        val result = ArrayList<KtFile>(0)
        for (dependency in dependencies) {
            if (dependency.platform.isCommon()) {
                val files = dependency.getCapability(MODULE_FILES)
                    ?: error("MODULE_FILES should have been set for the common module: $dependency")
                result.addAll(files)
            }
        }

        return result
    }

    private fun validateAndCompareDescriptorWithFile(
        expectedFile: File,
        testFiles: List<TestFile>,
        modules: Map<TestModule?, ModuleDescriptorImpl>,
        coroutinesPackage: String
    ) {
        if (skipDescriptorsValidation()) return
        if (testFiles.any { file -> InTextDirectivesUtils.isDirectiveDefined(file.expectedText, "// SKIP_TXT") }) {
            assertFalse(".txt file should not exist if SKIP_TXT directive is used: $expectedFile", expectedFile.exists())
            return
        }

        val comparator = RecursiveDescriptorComparator(createdAffectedPackagesConfiguration(testFiles, modules.values))

        val isMultiModuleTest = modules.size != 1

        val packages =
            (testFiles.flatMap {
                InTextDirectivesUtils.findListWithPrefixes(it.expectedText, "// RENDER_PACKAGE:").map {
                    FqName(it.trim())
                }
            } + FqName.ROOT).toSet()

        val textByPackage = packages.keysToMap { StringBuilder() }

        val sortedModules = modules.keys.sortedWith(Comparator { x, y ->
            when {
                x == null && y == null -> 0
                x == null && y != null -> -1
                x != null && y == null -> 1
                x != null && y != null -> x.compareTo(y)
                else -> error("Unreachable")
            }
        })

        for ((packageName, packageText) in textByPackage.entries) {
            val module = sortedModules.iterator()
            while (module.hasNext()) {
                val moduleDescriptor = modules[module.next()]!!

                val aPackage = moduleDescriptor.getPackage(packageName)
                assertFalse(aPackage.isEmpty())

                if (isMultiModuleTest) {
                    packageText.append(String.format("// -- Module: %s --\n", moduleDescriptor.name))
                }

                val actualSerialized = comparator.serializeRecursively(aPackage)
                packageText.append(actualSerialized)

                if (isMultiModuleTest && module.hasNext()) {
                    packageText.append("\n\n")
                }
            }
        }

        val allPackagesText = textByPackage.values.joinToString("\n")

        val lineCount = StringUtil.getLineBreakCount(allPackagesText)
        assert(lineCount < 1000) {
            "Rendered descriptors of this test take up $lineCount lines. " +
                    "Please ensure you don't render JRE contents to the .txt file. " +
                    "Such tests are hard to maintain, take long time to execute and are subject to sudden unreviewed changes anyway."
        }

        KotlinTestUtils.assertEqualsToFile(expectedFile, allPackagesText) { s ->
            s.replace("COROUTINES_PACKAGE", coroutinesPackage)
        }
    }


    fun skipDescriptorsValidation(): Boolean = false

    private fun getJavaFilePackage(testFile: TestFile): Name {
        val pattern = Pattern.compile("^\\s*package [.\\w\\d]*", Pattern.MULTILINE)
        val matcher = pattern.matcher(testFile.expectedText)

        if (matcher.find()) {
            return testFile.expectedText
                .substring(matcher.start(), matcher.end())
                .split(" ")
                .last()
                .filter { !it.isWhitespace() }
                .let { Name.identifier(it.split(".").first()) }
        }

        return SpecialNames.ROOT_PACKAGE
    }

    private fun createdAffectedPackagesConfiguration(
        testFiles: List<TestFile>,
        modules: Collection<ModuleDescriptor>
    ): RecursiveDescriptorComparator.Configuration {
        val packagesNames = (
                testFiles.filter { it.ktFile == null }
                    .map { getJavaFilePackage(it) } +
                        getTopLevelPackagesFromFileList(getKtFiles(testFiles, false))
                ).toSet()

        val stepIntoFilter = Predicate<DeclarationDescriptor> { descriptor ->
            val module = DescriptorUtils.getContainingModuleOrNull(descriptor)
            if (module !in modules) return@Predicate false

            if (descriptor is PackageViewDescriptor) {
                val fqName = descriptor.fqName
                return@Predicate fqName.isRoot || fqName.pathSegments().first() in packagesNames
            }

            true
        }

        return RECURSIVE.filterRecursion(stepIntoFilter)
            .withValidationStrategy(DescriptorValidator.ValidationVisitor.errorTypesAllowed())
            .checkFunctionContracts(true)
    }

    private fun getTopLevelPackagesFromFileList(files: List<KtFile>): Set<Name> =
        files.mapTo(LinkedHashSet<Name>()) { file ->
            file.packageFqName.pathSegments().firstOrNull() ?: SpecialNames.ROOT_PACKAGE
        }

    private fun createModules(
        groupedByModule: Map<TestModule?, List<TestFile>>,
        storageManager: StorageManager
    ): MutableMap<TestModule?, ModuleDescriptorImpl> {
        val modules = HashMap<TestModule?, ModuleDescriptorImpl>()

        for (testModule in groupedByModule.keys) {
            val module = if (testModule == null)
                createSealedModule(storageManager)
            else
                createModule(testModule.name, storageManager)

            modules.put(testModule, module)
        }

        for (testModule in groupedByModule.keys) {
            if (testModule == null) continue

            val module = modules[testModule]!!
            val dependencies = ArrayList<ModuleDescriptorImpl>()
            dependencies.add(module)
            for (dependency in testModule.getDependencies()) {
                if (dependency is TestModule) {
                    dependencies.add(modules[dependency]!!)
                }
            }

            dependencies.add(module.builtIns.builtInsModule)
            dependencies.addAll(getAdditionalDependencies(module))
            module.setDependencies(dependencies)
        }

        return modules
    }

    fun getAdditionalDependencies(module: ModuleDescriptorImpl): List<ModuleDescriptorImpl> =
        emptyList()

    fun createModule(moduleName: String, storageManager: StorageManager): ModuleDescriptorImpl {
        val platform = parseModulePlatformByName(moduleName)
        val builtIns = JvmBuiltIns(storageManager, JvmBuiltIns.Kind.FROM_CLASS_LOADER)
        return ModuleDescriptorImpl(Name.special("<$moduleName>"), storageManager, builtIns, platform)
    }

    fun createSealedModule(storageManager: StorageManager): ModuleDescriptorImpl =
        createModule("test-module-jvm", storageManager).apply {
            setDependencies(this, builtIns.builtInsModule)
        }

    private fun checkAllResolvedCallsAreCompleted(
        ktFiles: List<KtFile>,
        bindingContext: BindingContext,
        configuredLanguageVersionSettings: LanguageVersionSettings
    ) {
        if (ktFiles.any { file -> AnalyzingUtils.getSyntaxErrorRanges(file).isNotEmpty() }) return

        val resolvedCallsEntries = bindingContext.getSliceContents(BindingContext.RESOLVED_CALL)
        val unresolvedCallsOnElements = ArrayList<PsiElement>()

        for ((call, resolvedCall) in resolvedCallsEntries) {
            val element = call.callElement

            if (!configuredLanguageVersionSettings.supportsFeature(LanguageFeature.NewInference)) {
                if (!(resolvedCall as MutableResolvedCall<*>).isCompleted) {
                    unresolvedCallsOnElements.add(element)
                }
            }
        }

        if (unresolvedCallsOnElements.isNotEmpty()) {
            error(
                "There are uncompleted resolved calls for the following elements:\n" +
                        unresolvedCallsOnElements.joinToString(separator = "\n") { element ->
                            val lineAndColumn = DiagnosticUtils.getLineAndColumnInPsiFile(element.containingFile, element.textRange)
                            "'${element.text}'$lineAndColumn"
                        }
            )
        }

        checkResolvedCallsInDiagnostics(bindingContext, configuredLanguageVersionSettings)
    }

    private fun checkResolvedCallsInDiagnostics(
        bindingContext: BindingContext,
        configuredLanguageVersionSettings: LanguageVersionSettings
    ) {
        val diagnosticsStoringResolvedCalls1 = setOf(
            OVERLOAD_RESOLUTION_AMBIGUITY, NONE_APPLICABLE, CANNOT_COMPLETE_RESOLVE, UNRESOLVED_REFERENCE_WRONG_RECEIVER,
            ASSIGN_OPERATOR_AMBIGUITY, ITERATOR_AMBIGUITY
        )
        val diagnosticsStoringResolvedCalls2 = setOf(
            COMPONENT_FUNCTION_AMBIGUITY, DELEGATE_SPECIAL_FUNCTION_AMBIGUITY, DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE
        )

        for (diagnostic in bindingContext.diagnostics) {
            when (diagnostic.factory) {
                in diagnosticsStoringResolvedCalls1 -> assertResolvedCallsAreCompleted(
                    diagnostic, DiagnosticFactory.cast(diagnostic, diagnosticsStoringResolvedCalls1).a, configuredLanguageVersionSettings
                )
                in diagnosticsStoringResolvedCalls2 -> assertResolvedCallsAreCompleted(
                    diagnostic, DiagnosticFactory.cast(diagnostic, diagnosticsStoringResolvedCalls2).b, configuredLanguageVersionSettings
                )
            }
        }
    }

    private fun assertResolvedCallsAreCompleted(
        diagnostic: Diagnostic,
        resolvedCalls: Collection<ResolvedCall<*>>,
        configuredLanguageVersionSettings: LanguageVersionSettings
    ) {
        val element = diagnostic.psiElement
        val lineAndColumn = DiagnosticUtils.getLineAndColumnInPsiFile(element.containingFile, element.textRange)
        if (configuredLanguageVersionSettings.supportsFeature(LanguageFeature.NewInference)) return

        assertTrue("Resolved calls stored in ${diagnostic.factory.name}\nfor '${element.text}'$lineAndColumn are not completed",
                   resolvedCalls.all { (it as MutableResolvedCall<*>).isCompleted })
    }

    private val HASH_SANITIZER = fun(s: String): String = s.replace("@(\\d)+".toRegex(), "")

    private val MODULE_FILES = ModuleDescriptor.Capability<List<KtFile>>("")
}