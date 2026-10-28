package com.github.k7t3.tcv.app.secure;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import static java.lang.foreign.ValueLayout.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WindowsCredentialSecretStoreTest {
    @ParameterizedTest
    @CsvSource({"false,5", "true,5", "false,1312", "true,1312"})
    void accessAndLogonFailuresKeepTheirCodeWithoutRetryOrFallback(boolean write, int code) {
        try (var api = new MemoryCredentials()) {
            api.readStatus = code;
            api.writeStatus = code;
            var store = new WindowsCredentialSecretStore(api);
            var id = UUID.randomUUID().toString();

            assertThatThrownBy(() -> {
                if (write) store.save(id, new byte[32]);
                else store.read(id);
            }).isInstanceOfSatisfying(NativeSecretStoreException.class, failure -> assertThat(failure.code()).isEqualTo(code));

            assertThat(api.readCalls + api.writeCalls).isEqualTo(1);
            assertThat(api.frees).isZero();
            if (write) assertThat(api.borrowed.scope().isAlive()).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31, 33})
    void invalidSecretLengthsAreRejectedAndTheOwnedBufferIsStillClearedAndFreed(int length) {
        try (var api = new MemoryCredentials(length)) {
            var store = new WindowsCredentialSecretStore(api);

            assertThatThrownBy(() -> store.read(UUID.randomUUID().toString()))
                    .isInstanceOf(KeyManagerException.class).hasMessageContaining("length");

            assertThat(api.frees).isEqualTo(1);
        }
    }

    @Test
    void aNonGenericCredentialIsRejectedAndItsSecretIsStillClearedAndFreed() {
        try (var api = new MemoryCredentials()) {
            api.credential.set(JAVA_INT, 4, 2);
            var store = new WindowsCredentialSecretStore(api);

            assertThatThrownBy(() -> store.read(UUID.randomUUID().toString()))
                    .isInstanceOf(KeyManagerException.class).hasMessageContaining("type");

            assertThat(api.frees).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theForeignFunctionAdapterUsesTheWin32SignaturesWithoutLoadingWindowsLibraries(boolean write) throws Exception {
        try (var api = new MemoryCredentials(); var stubs = Arena.ofConfined()) {
            var symbols = new HashMap<String, MemorySegment>();
            var lookup = MethodHandles.lookup();
            var linker = Linker.nativeLinker();
            symbols.put("CredReadW", linker.upcallStub(lookup.findVirtual(MemoryCredentials.class, "nativeRead",
                    MethodType.methodType(int.class, MemorySegment.class, int.class, int.class, MemorySegment.class)).bindTo(api),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS), stubs));
            symbols.put("CredWriteW", linker.upcallStub(lookup.findVirtual(MemoryCredentials.class, "nativeWrite",
                    MethodType.methodType(int.class, MemorySegment.class, int.class)).bindTo(api),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT), stubs));
            symbols.put("CredFree", linker.upcallStub(lookup.findVirtual(MemoryCredentials.class, "nativeFree",
                    MethodType.methodType(void.class, MemorySegment.class)).bindTo(api), FunctionDescriptor.ofVoid(ADDRESS), stubs));
            var errorName = System.getProperty("os.name").startsWith("Windows") ? "GetLastError" : "errno";
            var foreign = new WindowsCredentialSecretStore.NativeCredentialApi(name -> java.util.Optional.ofNullable(symbols.get(name)), errorName);
            var store = new WindowsCredentialSecretStore(foreign);
            var id = UUID.randomUUID().toString();

            if (write) store.save(id, new byte[32]);
            else assertThat(store.read(id)).hasSize(32).containsOnly((byte) 7);

            assertThat(api.callbackFailure).isNull();
            assertThat(api.target).isEqualTo("com.github.k7t3.tcv/key-protection/" + id);
            assertThat(api.frees).isEqualTo(write ? 0 : 1);
        }
    }

    @Test
    void onlyTheDocumentedMissingCredentialCodeMeansAbsent() {
        try (var api = new MemoryCredentials()) {
            api.readStatus = 1168;
            var store = new WindowsCredentialSecretStore(api);

            assertThat(store.read(UUID.randomUUID().toString())).isNull();

            assertThat(api.readCalls).isEqualTo(1);
            assertThat(api.frees).isZero();
        }
    }

    @Test
    void aSaveUsesAPersistentUserCredentialAndBorrowsTheInputWithoutChangingIt() {
        var id = UUID.randomUUID().toString();
        var secret = new byte[32];
        Arrays.fill(secret, (byte) 11);
        try (var api = new MemoryCredentials()) {
            var store = new WindowsCredentialSecretStore(api);

            store.save(id, secret);

            assertThat(api.saved).containsExactly(secret);
            assertThat(api.target).isEqualTo("com.github.k7t3.tcv/key-protection/" + id);
            assertThat(api.borrowed.scope().isAlive()).isFalse();
            assertThat(secret).containsOnly((byte) 11);
            assertThat(api.frees).isZero();
        }
    }

    @Test
    void aReadReturnsAnIndependentCopyAndClearsTheNativeCredentialBeforeFreeingIt() {
        var id = UUID.randomUUID().toString();
        try (var api = new MemoryCredentials()) {
            var store = new WindowsCredentialSecretStore(api);

            var secret = store.read(id);

            assertThat(secret).hasSize(32).containsOnly((byte) 7);
            assertThat(api.target).isEqualTo("com.github.k7t3.tcv/key-protection/" + id);
            assertThat(api.frees).isEqualTo(1);
            assertThat(api.blob.toArray(JAVA_BYTE)).containsOnly((byte) 0);
            Arrays.fill(secret, (byte) 0);
        }
    }

    private static final class MemoryCredentials implements WindowsCredentialSecretStore.CredentialApi, AutoCloseable {
        private final Arena arena = Arena.ofConfined();
        private final MemorySegment credential = arena.allocate(80, 8);
        private final MemorySegment blob;
        private String target;
        private int frees;
        private byte[] saved;
        private MemorySegment borrowed;
        private int readStatus;
        private int readCalls;
        private int writeCalls;
        private int writeStatus;
        private Throwable callbackFailure;

        private int nativeRead(MemorySegment name, int type, int flags, MemorySegment result) {
            try {
                assertThat(type).isEqualTo(1);
                assertThat(flags).isZero();
                return read(name, result.reinterpret(ADDRESS.byteSize())) == 0 ? 1 : 0;
            } catch (Throwable failure) { callbackFailure = failure; return 0; }
        }

        private int nativeWrite(MemorySegment value, int flags) {
            try {
                assertThat(flags).isZero();
                return write(value.reinterpret(80)) == 0 ? 1 : 0;
            } catch (Throwable failure) { callbackFailure = failure; return 0; }
        }

        private void nativeFree(MemorySegment value) {
            try { free(value); }
            catch (Throwable failure) { callbackFailure = failure; }
        }

        private MemoryCredentials() { this(32); }

        private MemoryCredentials(int length) {
            blob = arena.allocate(length);
            blob.fill((byte) 7);
            credential.set(JAVA_INT, 4, 1);
            credential.set(JAVA_INT, 32, length);
            credential.set(ADDRESS, 40, blob);
        }

        @Override public int read(MemorySegment name, MemorySegment result) {
            readCalls++;
            target = name.reinterpret(1024).getString(0, StandardCharsets.UTF_16LE);
            if (readStatus != 0) return readStatus;
            result.set(ADDRESS, 0, credential);
            return 0;
        }
        @Override public int write(MemorySegment value) {
            writeCalls++;
            assertThat(value.byteSize()).isEqualTo(80);
            assertThat(value.get(JAVA_INT, 0)).isZero();
            assertThat(value.get(JAVA_INT, 4)).isEqualTo(1);
            assertThat(value.get(JAVA_INT, 32)).isEqualTo(32);
            assertThat(value.get(JAVA_INT, 48)).as("CRED_PERSIST_LOCAL_MACHINE: same user, later logons, no roaming").isEqualTo(2);
            assertThat(value.get(JAVA_INT, 52)).isZero();
            for (var offset : new long[] {16, 56, 64, 72}) assertThat(value.get(ADDRESS, offset)).isEqualTo(MemorySegment.NULL);
            target = value.get(ADDRESS, 8).reinterpret(1024).getString(0, StandardCharsets.UTF_16LE);
            saved = value.get(ADDRESS, 40).reinterpret(32).toArray(JAVA_BYTE);
            borrowed = value;
            return writeStatus;
        }
        @Override public void free(MemorySegment value) {
            assertThat(value.address()).isEqualTo(credential.address());
            assertThat(blob.toArray(JAVA_BYTE)).containsExactly(new byte[(int) blob.byteSize()]);
            frees++;
        }
        @Override public void close() { arena.close(); }
    }
}
