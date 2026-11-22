package com.github.k7t3.tcv.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import javax.inject.Inject

/** コンパイル・テストと同じ toolchain の JDK ツールを使う。 */
abstract class JdkToolTask extends DefaultTask {
    @Nested abstract Property<JavaLauncher> getJavaLauncher()
    @Input abstract Property<Integer> getJavaFeatureVersion()
    @Inject protected abstract ExecOperations getExecOperations()
    @Internal protected abstract String getToolName()

    @Internal String getExecutable() {
        def suffix = System.getProperty('os.name').toLowerCase(Locale.ROOT).startsWith('windows') ? '.exe' : ''
        javaLauncher.get().metadata.installationPath.file("bin/${toolName}${suffix}").asFile.absolutePath
    }

    protected void verifyToolchain() {
        def metadata = javaLauncher.get().metadata
        if (metadata.languageVersion.asInt() != javaFeatureVersion.get() || !new File(executable).isFile()) {
            throw new GradleException("${toolName} requires a complete Java ${javaFeatureVersion.get()} JDK: ${metadata.installationPath}")
        }
        logger.lifecycle("${toolName}: ${executable} (Java ${metadata.javaRuntimeVersion})")
    }
}
