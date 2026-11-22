package com.github.k7t3.tcv.build

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@DisableCachingByDefault(because = 'Invokes the selected native JDK tool')
abstract class JdkModulesTask extends JdkToolTask {
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE) abstract DirectoryProperty getInputDirectory()
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE) abstract DirectoryProperty getModuleDirectory()
    @Input abstract Property<String> getMainJar()
    @Input abstract ListProperty<String> getJavafxModules()
    @OutputFile abstract RegularFileProperty getModulesFile()
    @Override protected String getToolName() { 'jdeps' }

    @TaskAction void inspectModules() {
        verifyToolchain()
        def output = new ByteArrayOutputStream()
        def errors = new ByteArrayOutputStream()
        def toolExecutable = executable
        def result = execOperations.exec {
            executable = toolExecutable
            args '--module-path', moduleDirectory.get().asFile.absolutePath,
                    '--add-modules', 'ALL-MODULE-PATH', '--multi-release', javaFeatureVersion.get().toString(),
                    '--ignore-missing-deps', '--print-module-deps',
                    '--class-path', new File(inputDirectory.get().asFile, '*').absolutePath,
                    inputDirectory.file(mainJar).get().asFile.absolutePath
            standardOutput = output
            errorOutput = errors
            ignoreExitValue = true
        }
        if (result.exitValue != 0) {
            logger.error(output.toString(StandardCharsets.UTF_8))
            logger.error(errors.toString(StandardCharsets.UTF_8))
            result.assertNormalExitValue()
        }
        def modules = PackagePlan.modules(output.toString(StandardCharsets.UTF_8), javafxModules.get())
        def target = modulesFile.get().asFile.toPath()
        Files.createDirectories(target.parent)
        Files.writeString(target, modules.join(',') + '\n', StandardCharsets.UTF_8)
        logger.lifecycle("Runtime modules: ${modules.join(',')}")
    }
}
