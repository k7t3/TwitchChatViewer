package com.github.k7t3.tcv.app.secure;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.lang.foreign.ValueLayout.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibsecretSecretStoreTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 31, 33})
    void invalidLengthsAreRejectedBeforeCopyingAndTheValueIsStillReleased(int length) {
        try (var api = new MemorySecretService()) {
            api.length = length;
            var store = new LibsecretSecretStore(api);

            assertThatThrownBy(() -> store.read(UUID.randomUUID().toString()))
                    .isInstanceOf(KeyManagerException.class).hasMessageContaining("length");

            assertThat(api.releasedValues).isEqualTo(1);
            assertThat(api.releasedAttributes).isEqualTo(1);
        }
    }

    @Test
    void aNullValueWithoutAnOsErrorMeansMissing() {
        try (var api = new MemorySecretService()) {
            api.missing = true;
            var store = new LibsecretSecretStore(api);

            assertThat(store.read(UUID.randomUUID().toString())).isNull();

            assertThat(api.releasedAttributes).isEqualTo(1);
            assertThat(api.releasedValues).isZero();
            assertThat(api.releasedErrors).isZero();
        }
    }

    @Test
    void aSaveErrorKeepsItsNativeCodeAndReleasesBothTheValueAndError() {
        try (var api = new MemorySecretService()) {
            api.errorCode = 77;
            var store = new LibsecretSecretStore(api);

            assertThatThrownBy(() -> store.save(UUID.randomUUID().toString(), new byte[32]))
                    .isInstanceOfSatisfying(NativeSecretStoreException.class, failure -> assertThat(failure.code()).isEqualTo(77));

            assertThat(api.releasedAttributes).isEqualTo(1);
            assertThat(api.releasedValues).isEqualTo(1);
            assertThat(api.releasedErrors).isEqualTo(1);
            assertThat(api.input.scope().isAlive()).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "save", "error"})
    void theForeignFunctionAdapterUsesTheBinaryLibsecretContractWithoutLoadingLinuxLibraries(String operation) throws Exception {
        try (var api = new MemorySecretService(); var stubs = Arena.ofConfined()) {
            var functions = new HashMap<String, MemorySegment>();
            functions.put("g_str_hash", MemorySegment.ofAddress(1));
            functions.put("g_str_equal", MemorySegment.ofAddress(2));
            functions.put("g_hash_table_new", stub(stubs, api, "nativeAttributes", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS)));
            functions.put("g_hash_table_insert", stub(stubs, api, "nativeInsert", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)));
            functions.put("g_hash_table_unref", stub(stubs, api, "nativeReleaseAttributes", FunctionDescriptor.ofVoid(ADDRESS)));
            functions.put("g_error_free", stub(stubs, api, "nativeReleaseError", FunctionDescriptor.ofVoid(ADDRESS)));
            functions.put("secret_password_lookupv_binary_sync", stub(stubs, api, "nativeRead", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS)));
            functions.put("secret_password_storev_binary_sync", stub(stubs, api, "nativeSave", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS)));
            functions.put("secret_value_new", stub(stubs, api, "nativeValue", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, ADDRESS)));
            functions.put("secret_value_get", stub(stubs, api, "nativeData", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS)));
            functions.put("secret_value_unref", stub(stubs, api, "nativeReleaseValue", FunctionDescriptor.ofVoid(ADDRESS)));
            SymbolLookup symbols = name -> Optional.ofNullable(functions.get(name));
            var store = new LibsecretSecretStore(new LibsecretSecretStore.NativeSecretApi(symbols, symbols));
            var id = UUID.randomUUID().toString();
            if (operation.equals("error")) api.errorCode = 77;

            if (operation.equals("save")) {
                store.save(id, new byte[32]);
                assertThat(api.saved).containsExactly(new byte[32]);
            } else if (operation.equals("error")) {
                assertThatThrownBy(() -> store.read(id)).isInstanceOfSatisfying(NativeSecretStoreException.class,
                        failure -> assertThat(failure.code()).isEqualTo(77));
            } else assertThat(store.read(id)).hasSize(32).containsOnly((byte) 7);

            assertThat(api.callbackFailure).isNull();
            assertThat(api.application).isEqualTo("com.github.k7t3.tcv");
            assertThat(api.id).isEqualTo(id);
            assertThat(api.releasedAttributes).isEqualTo(1);
            assertThat(api.releasedValues).isEqualTo(operation.equals("error") ? 0 : 1);
            assertThat(api.releasedErrors).isEqualTo(operation.equals("error") ? 1 : 0);
        }
    }

    private static MemorySegment stub(Arena arena, MemorySecretService target, String method, FunctionDescriptor descriptor) throws Exception {
        var handle = MethodHandles.lookup().findVirtual(MemorySecretService.class, method, descriptor.toMethodType()).bindTo(target);
        return Linker.nativeLinker().upcallStub(handle, descriptor, arena);
    }

    @Test
    void anUnsuccessfulSaveWithoutGErrorStillFailsAndReleasesItsResources() {
        try (var api = new MemorySecretService()) {
            api.saveResult = 0;
            var store = new LibsecretSecretStore(api);

            assertThatThrownBy(() -> store.save(UUID.randomUUID().toString(), new byte[32]))
                    .isInstanceOfSatisfying(NativeSecretStoreException.class, failure -> assertThat(failure.code()).isEqualTo(-1));

            assertThat(api.releasedValues).isEqualTo(1);
            assertThat(api.releasedAttributes).isEqualTo(1);
            assertThat(api.input.scope().isAlive()).isFalse();
        }
    }

    @Test
    void savingKeepsEmbeddedZeroBytesAndClosesItsBorrowedNativeInput() {
        var secret = new byte[32];
        secret[1] = (byte) 255;
        try (var api = new MemorySecretService()) {
            var store = new LibsecretSecretStore(api);

            store.save(UUID.randomUUID().toString(), secret);

            assertThat(api.saved).containsExactly(secret);
            assertThat(secret[1]).isEqualTo((byte) 255);
            assertThat(api.input.scope().isAlive()).isFalse();
            assertThat(api.releasedValues).isEqualTo(1);
            assertThat(api.releasedAttributes).isEqualTo(1);
        }
    }

    @Test
    void anOsReadErrorIsNotTreatedAsAMissingSecretAndItsErrorIsReleased() {
        try (var api = new MemorySecretService()) {
            api.errorCode = 77;
            var store = new LibsecretSecretStore(api);

            assertThatThrownBy(() -> store.read(UUID.randomUUID().toString()))
                    .isInstanceOfSatisfying(NativeSecretStoreException.class, failure -> assertThat(failure.code()).isEqualTo(77));

            assertThat(api.releasedErrors).isEqualTo(1);
            assertThat(api.releasedAttributes).isEqualTo(1);
            assertThat(api.releasedValues).isZero();
        }
    }

    @Test
    void readingCopiesTheBinarySecretBeforeReleasingTheOwnedNativeValue() {
        var id = UUID.randomUUID().toString();
        try (var api = new MemorySecretService()) {
            var store = new LibsecretSecretStore(api);

            var secret = store.read(id);

            assertThat(secret).hasSize(32).containsOnly((byte) 7);
            assertThat(api.application).isEqualTo("com.github.k7t3.tcv");
            assertThat(api.id).isEqualTo(id);
            assertThat(api.releasedValues).isEqualTo(1);
            assertThat(api.releasedAttributes).isEqualTo(1);
            assertThat(api.bytes.toArray(JAVA_BYTE)).containsExactly(new byte[32]);
        }
    }

    private static final class MemorySecretService implements LibsecretSecretStore.SecretApi, AutoCloseable {
        private final Arena arena = Arena.ofConfined();
        private final MemorySegment attributes = arena.allocate(1);
        private final MemorySegment value = arena.allocate(1);
        private final MemorySegment bytes = arena.allocate(32);
        private String application;
        private String id;
        private int releasedAttributes;
        private int releasedValues;
        private int errorCode;
        private int releasedErrors;
        private final MemorySegment nativeError = arena.allocate(8, 4);
        private MemorySegment input;
        private byte[] saved;
        private int saveResult = 1;
        private Throwable callbackFailure;
        private long length = 32;
        private boolean missing;

        private <T> T guarded(Supplier<T> action, T failed) {
            try { return action.get(); }
            catch (Throwable failure) { callbackFailure = failure; return failed; }
        }

        private MemorySegment nativeAttributes(MemorySegment hash, MemorySegment equal) {
            return guarded(() -> {
                assertThat(hash.address()).isEqualTo(1);
                assertThat(equal.address()).isEqualTo(2);
                return attributes;
            }, MemorySegment.NULL);
        }

        private int nativeInsert(MemorySegment table, MemorySegment key, MemorySegment text) {
            return guarded(() -> {
                assertThat(table.address()).isEqualTo(attributes.address());
                var name = key.reinterpret(256).getString(0);
                var content = text.reinterpret(256).getString(0);
                if (name.equals("application")) application = content;
                else if (name.equals("key-id")) id = content;
                else throw new AssertionError("unknown public attribute " + name);
                return 1;
            }, 0);
        }

        private MemorySegment nativeRead(MemorySegment schema, MemorySegment table, MemorySegment cancellable, MemorySegment error) {
            return guarded(() -> {
                assertThat(schema).isEqualTo(MemorySegment.NULL);
                assertThat(cancellable).isEqualTo(MemorySegment.NULL);
                return read(table, error.reinterpret(ADDRESS.byteSize()));
            }, MemorySegment.NULL);
        }

        private int nativeSave(MemorySegment schema, MemorySegment table, MemorySegment collection, MemorySegment label,
                               MemorySegment value, MemorySegment cancellable, MemorySegment error) {
            return guarded(() -> {
                assertThat(schema).isEqualTo(MemorySegment.NULL);
                assertThat(collection).as("persistent default collection, not session collection").isEqualTo(MemorySegment.NULL);
                assertThat(cancellable).isEqualTo(MemorySegment.NULL);
                assertThat(label.reinterpret(256).getString(0)).isEqualTo("Twitch Chat Viewer key protection");
                return save(table, value, error.reinterpret(ADDRESS.byteSize()));
            }, 0);
        }

        private MemorySegment nativeValue(MemorySegment secret, long length, MemorySegment contentType) {
            return guarded(() -> {
                assertThat(contentType.reinterpret(256).getString(0)).isEqualTo("application/octet-stream");
                return value(secret.reinterpret(length), length);
            }, MemorySegment.NULL);
        }

        private MemorySegment nativeData(MemorySegment value, MemorySegment length) {
            return guarded(() -> data(value, length.reinterpret(JAVA_LONG.byteSize())), MemorySegment.NULL);
        }

        private void nativeReleaseAttributes(MemorySegment attributes) { guarded(() -> { releaseAttributes(attributes); return true; }, false); }
        private void nativeReleaseValue(MemorySegment value) { guarded(() -> { releaseValue(value); return true; }, false); }
        private void nativeReleaseError(MemorySegment error) { guarded(() -> { releaseError(error); return true; }, false); }

        private MemorySecretService() { bytes.fill((byte) 7); }

        @Override public MemorySegment attributes(MemorySegment application, MemorySegment id) {
            this.application = application.getString(0);
            this.id = id.getString(0);
            return attributes;
        }
        @Override public MemorySegment read(MemorySegment attributes, MemorySegment error) {
            assertThat(attributes.address()).isEqualTo(this.attributes.address());
            assertThat(error.get(ADDRESS, 0)).isEqualTo(MemorySegment.NULL);
            if (errorCode != 0) {
                nativeError.set(JAVA_INT, 4, errorCode);
                error.set(ADDRESS, 0, nativeError);
                return MemorySegment.NULL;
            }
            return missing ? MemorySegment.NULL : value;
        }
        @Override public MemorySegment data(MemorySegment value, MemorySegment length) {
            assertThat(value.address()).isEqualTo(this.value.address());
            length.set(JAVA_LONG, 0, this.length);
            return bytes;
        }
        @Override public MemorySegment value(MemorySegment secret, long length) {
            assertThat(length).isEqualTo(32);
            input = secret;
            MemorySegment.copy(secret, 0, bytes, 0, length);
            return value;
        }
        @Override public int save(MemorySegment attributes, MemorySegment value, MemorySegment error) {
            assertThat(attributes.address()).isEqualTo(this.attributes.address());
            assertThat(value.address()).isEqualTo(this.value.address());
            assertThat(error.get(ADDRESS, 0)).isEqualTo(MemorySegment.NULL);
            saved = bytes.toArray(JAVA_BYTE);
            if (errorCode != 0) {
                nativeError.set(JAVA_INT, 4, errorCode);
                error.set(ADDRESS, 0, nativeError);
                return 0;
            }
            return saveResult;
        }
        @Override public void releaseAttributes(MemorySegment attributes) {
            assertThat(attributes.address()).isEqualTo(this.attributes.address());
            releasedAttributes++;
        }
        @Override public void releaseValue(MemorySegment value) {
            assertThat(value.address()).isEqualTo(this.value.address());
            bytes.fill((byte) 0);
            releasedValues++;
        }
        @Override public void releaseError(MemorySegment error) {
            assertThat(error.address()).isEqualTo(nativeError.address());
            releasedErrors++;
        }
        @Override public void close() { arena.close(); }
    }
}
