package com.github.k7t3.tcv.build

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@DisableCachingByDefault(because = 'Native packaging also depends on external OS tools')
abstract class NativePackageTask extends JdkToolTask {
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE) abstract DirectoryProperty getInputDirectory()
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE) abstract DirectoryProperty getModuleDirectory()
    @InputFile @PathSensitive(PathSensitivity.NONE) abstract RegularFileProperty getModulesFile()
    @Input abstract Property<String> getMainJar()
    @Input abstract Property<String> getMainClass()
    @Input abstract Property<String> getFullVersion()
    @Input abstract Property<String> getPlatformName()
    @Input abstract Property<Boolean> getAppImage()
    @Internal abstract DirectoryProperty getProjectRoot()
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE) abstract ConfigurableFileCollection getPackageResources()
    @OutputDirectory abstract DirectoryProperty getOutputDirectory()
    @LocalState abstract RegularFileProperty getLogFile()
    @Override protected String getToolName() { 'jpackage' }

    NativePackageTask() {
        // WiX / hdiutil / dpkg と署名環境は Gradle の入力だけでは追跡できない。
        outputs.upToDateWhen { false }
    }

    @TaskAction void createPackage() {
        verifyToolchain()
        def platform = PackagePlan.Platform.valueOf(platformName.get())
        def modules = PackagePlan.modules(Files.readString(modulesFile.get().asFile.toPath()).trim(), [])
        def input = new PackagePlan.Inputs(platform, appImage.get(), fullVersion.get(),
                inputDirectory.get().asFile.toPath(), moduleDirectory.get().asFile.toPath(),
                mainJar.get(), mainClass.get(), projectRoot.get().asFile.toPath(), modules)
        PackagePlan.validateInputs(input)
        def log = logFile.get().asFile.toPath()
        def toolExecutable = executable
        Files.createDirectories(log.parent)
        def produced = PackageOutput.produce(outputDirectory.get().asFile.toPath(), { staging ->
            Files.newOutputStream(log).withCloseable { stream ->
                def result = execOperations.exec {
                    executable = toolExecutable
                    args PackagePlan.arguments(input, staging)
                    standardOutput = stream
                    errorOutput = stream
                    ignoreExitValue = true
                }
                if (result.exitValue != 0) {
                    throw new GradleException("jpackage failed (${result.exitValue}); see ${log.toAbsolutePath()}")
                }
            }
        } as PackageOutput.Generator)
        logger.lifecycle("Created package: ${produced.output()}; log: ${log.toAbsolutePath()}")
        produced.cleanupFailures().each { cleanup ->
            logger.warn("Package was created, but cleanup failed for owned directory ${cleanup.directory()}", cleanup.failure())
        }
    }
}
