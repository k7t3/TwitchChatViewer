package com.github.k7t3.tcv.app.secure;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretStoreHelperTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "autolaunch:", "unix:path=/fixture;autolaunch:"})
    void linuxRequiresAnExplicitSessionBusAndNeverAutolaunchesOne(String bus) {
        assertThatThrownBy(() -> SecretStoreHelper.nativeStore("Linux", bus))
                .isInstanceOfSatisfying(NativeSecretStoreException.class, failure -> assertThat(failure.code()).isEqualTo(-1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Darwin", "Plan 9"})
    void unknownPlatformsFailWithoutUsingAnotherPlatformsStore(String name) {
        assertThatThrownBy(() -> SecretStoreHelper.nativeStore(name, "unix:path=/fixture"))
                .isInstanceOfSatisfying(NativeSecretStoreException.class, failure -> assertThat(failure.code()).isEqualTo(-1));
    }

    @ParameterizedTest
    @MethodSource("platforms")
    void eachPlatformSelectsItsNativeBackendWithoutOpeningStorage(String name, Class<?> backend) {
        assertThat(SecretStoreHelper.nativeStore(name, "unix:path=/run/user/fixture/bus")).isInstanceOf(backend);
    }

    private static Stream<Arguments> platforms() {
        return Stream.of(Arguments.of("Mac OS X", MacKeychainSecretStore.class),
                Arguments.of("WINDOWS 11", WindowsCredentialSecretStore.class),
                Arguments.of("Linux", LibsecretSecretStore.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidInputExitsQuietlyBeforeSelectingAnyNativeBackend(boolean extraArgument) throws Exception {
        var classes = Path.of(KeyManagerImpl.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-Dos.name=UnsupportedTestOs", "-cp", classes.toString(),
                "com.github.k7t3.tcv.app.secure.SecretStoreHelper"));
        if (extraArgument) command.add("unexpected");
        var process = new ProcessBuilder(command).start();
        try {
            process.getOutputStream().close();

            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();

            assertThat(process.exitValue()).isEqualTo(2);
            assertThat(process.getInputStream().readAllBytes()).isEmpty();
            assertThat(process.getErrorStream().readAllBytes()).isEmpty();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            process.getInputStream().close();
            process.getErrorStream().close();
        }
    }
}
