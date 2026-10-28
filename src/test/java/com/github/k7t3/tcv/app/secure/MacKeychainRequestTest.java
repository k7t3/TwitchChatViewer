package com.github.k7t3.tcv.app.secure;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static java.lang.foreign.ValueLayout.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Securityの保管APIは全て偽物へ差し替える。実OSではCoreFoundationのプロセス内メモリだけを扱う。 */
@EnabledOnOs(OS.MAC)
class MacKeychainRequestTest {
    private static final SymbolLookup CF = SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());
    private static final SymbolLookup SECURITY = SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/Security.framework/Security", Arena.global());

    @ParameterizedTest
    @CsvSource({"open,-25291", "read,-25293", "save,-25299"})
    void nativeFailuresKeepTheirStatusWithoutRetryOrAnotherStorageScope(String operation, int code) {
        var path = operation.equals("open") ? Path.of("/synthetic-test-only/keys.keychain-db") : null;
        var api = new MemorySecurity(path);
        if (operation.equals("open")) api.openStatus = code;
        else if (operation.equals("read")) api.copyStatus = code;
        else api.addStatus = code;
        var store = new MacKeychainSecretStore("com.github.k7t3.tcv.test.keys", path, api);
        var id = UUID.randomUUID().toString();

        assertThatThrownBy(() -> {
            if (operation.equals("save")) store.save(id, new byte[32]);
            else store.read(id);
        }).isInstanceOf(NativeSecretStoreException.class)
                .satisfies(failure -> assertThat(((NativeSecretStoreException) failure).code()).isEqualTo(code));

        assertThat(api.openCount + api.copyCount + api.addCount).isEqualTo(1);
        assertThat(api.values).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31, 33})
    void aStoredValueWithAnUnexpectedLengthCannotBecomeAProtectionSecret(int length) {
        var api = new MemorySecurity(null);
        var id = UUID.randomUUID().toString();
        api.values.put(id, new byte[length]);
        var store = new MacKeychainSecretStore("com.github.k7t3.tcv.test.keys", null, api);

        assertThatThrownBy(() -> store.read(id)).isInstanceOf(KeyManagerException.class).hasMessageContaining("length");

        assertThat(api.copyCount).isEqualTo(1);
        assertThat(api.addCount).isZero();
    }

    @Test
    void aNonDataNativeResultIsRejectedBeforeItsMemoryIsReadAsSecretBytes() {
        var api = new MemorySecurity(null);
        var id = UUID.randomUUID().toString();
        api.values.put(id, new byte[32]);
        api.stringResult = true;
        var store = new MacKeychainSecretStore("com.github.k7t3.tcv.test.keys", null, api);

        assertThatThrownBy(() -> store.read(id)).isInstanceOf(KeyManagerException.class).hasMessageContaining("type");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void requestsPreserveServiceAccountBytesAndAnExplicitKeychainScope(boolean dedicated) {
        var path = dedicated ? Path.of("/synthetic-test-only/keys.keychain-db") : null;
        var api = new MemorySecurity(path);
        var store = new MacKeychainSecretStore("com.github.k7t3.tcv.test.keys", path, api);
        var id = UUID.randomUUID().toString();
        var secret = new byte[32];
        for (var i = 0; i < secret.length; i++) secret[i] = (byte) i;
        var original = secret.clone();

        assertThat(store.read(id)).isNull();
        store.save(id, secret);
        var restored = store.read(id);

        assertThat(restored).containsExactly(secret).isNotSameAs(secret);
        assertThat(api.service).isEqualTo("com.github.k7t3.tcv.test.keys");
        assertThat(api.account).isEqualTo(id);
        assertThat(api.openCount).isEqualTo(dedicated ? 3 : 0);
        assertThat(secret).containsExactly(original);
    }

    private static final class MemorySecurity implements MacKeychainSecretStore.SecurityApi {
        final Map<String, byte[]> values = new HashMap<>();
        final Path keychain;
        String service;
        String account;
        int openCount;
        int copyCount;
        int addCount;
        int openStatus;
        int copyStatus;
        int addStatus;
        boolean stringResult;

        MemorySecurity(Path keychain) { this.keychain = keychain; }

        @Override public int open(MemorySegment path, MemorySegment result) {
            assertThat(keychain).isNotNull();
            assertThat(path.getString(0)).isEqualTo(keychain.toString());
            openCount++;
            if (openStatus != 0) return openStatus;
            try (var arena = Arena.ofConfined()) {
                var ref = (MemorySegment) call(CF, "CFStringCreateWithCString",
                        FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT),
                        MemorySegment.NULL, arena.allocateFrom(keychain.toString()), 0x08000100);
                result.set(ADDRESS, 0, ref);
            }
            return 0;
        }

        @Override public int add(MemorySegment attributes) {
            addCount++;
            inspect(attributes, false);
            if (addStatus != 0) return addStatus;
            values.put(account, bytes(value(attributes, "kSecValueData")));
            return 0;
        }

        @Override public int copy(MemorySegment query, MemorySegment result) {
            copyCount++;
            inspect(query, true);
            if (copyStatus != 0) return copyStatus;
            assertThat(value(query, "kSecReturnData").address()).isEqualTo(constant(CF, "kCFBooleanTrue").address());
            var data = values.get(account);
            if (data == null) return -25300;
            try (var arena = Arena.ofConfined()) {
                var ref = stringResult
                        ? (MemorySegment) call(CF, "CFStringCreateWithCString", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT),
                                MemorySegment.NULL, arena.allocateFrom("not a data object"), 0x08000100)
                        : (MemorySegment) call(CF, "CFDataCreate", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG),
                                MemorySegment.NULL, arena.allocateFrom(JAVA_BYTE, data), (long) data.length);
                result.set(ADDRESS, 0, ref);
            }
            return 0;
        }

        private void inspect(MemorySegment query, boolean read) {
            assertThat(value(query, "kSecClass").address()).isEqualTo(constant(SECURITY, "kSecClassGenericPassword").address());
            service = string(value(query, "kSecAttrService"));
            account = string(value(query, "kSecAttrAccount"));
            var scope = value(query, read ? "kSecMatchSearchList" : "kSecUseKeychain");
            if (keychain == null) assertThat(scope.address()).isZero();
            else {
                var ref = read ? (MemorySegment) call(CF, "CFArrayGetValueAtIndex",
                        FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG), scope, 0L) : scope;
                assertThat(string(ref)).isEqualTo(keychain.toString());
            }
        }
    }

    private static MemorySegment value(MemorySegment dictionary, String key) {
        return (MemorySegment) call(CF, "CFDictionaryGetValue", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS),
                dictionary, constant(SECURITY, key));
    }

    private static MemorySegment constant(SymbolLookup library, String name) {
        return library.findOrThrow(name).reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
    }

    private static String string(MemorySegment value) {
        try (var arena = Arena.ofConfined()) {
            var buffer = arena.allocate(4096);
            var success = (byte) call(CF, "CFStringGetCString", FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT),
                    value, buffer, 4096L, 0x08000100);
            assertThat(success).isEqualTo((byte) 1);
            return buffer.getString(0);
        }
    }

    private static byte[] bytes(MemorySegment data) {
        var length = (long) call(CF, "CFDataGetLength", FunctionDescriptor.of(JAVA_LONG, ADDRESS), data);
        var pointer = (MemorySegment) call(CF, "CFDataGetBytePtr", FunctionDescriptor.of(ADDRESS, ADDRESS), data);
        return pointer.reinterpret(length).toArray(JAVA_BYTE);
    }

    private static Object call(SymbolLookup library, String name, FunctionDescriptor descriptor, Object... arguments) {
        try { return Linker.nativeLinker().downcallHandle(library.findOrThrow(name), descriptor).invokeWithArguments(arguments); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new AssertionError(failure); }
    }
}
