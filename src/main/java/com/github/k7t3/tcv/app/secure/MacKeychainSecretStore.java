package com.github.k7t3.tcv.app.secure;

import com.github.k7t3.tcv.lifecycle.CloseActions;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import static java.lang.foreign.ValueLayout.*;

/** macOSのSecItem要求とCoreFoundation資源を一回の呼出区間で扱う。 */
final class MacKeychainSecretStore implements OsSecretStore {
    private final String service;
    private final Path keychain;
    private final SecurityApi api;

    /** 戻り値はOSStatus。成功時に返したCFオブジェクトの所有を呼出元へ移す。 */
    interface SecurityApi {
        int open(MemorySegment path, MemorySegment result);
        int add(MemorySegment attributes);
        int copy(MemorySegment query, MemorySegment result);
    }

    MacKeychainSecretStore(String service, Path keychain) { this(service, keychain, new NativeSecurityApi()); }

    MacKeychainSecretStore(String service, Path keychain, SecurityApi api) {
        this.service = Objects.requireNonNull(service);
        if (service.isBlank() || service.indexOf('\0') >= 0) throw new IllegalArgumentException("invalid keychain service");
        this.keychain = keychain;
        this.api = Objects.requireNonNull(api);
    }

    @Override public byte[] read(String id) {
        try (var scope = new Scope()) {
            var query = query(scope, id, true);
            put(query, "kSecReturnData", constant(Native.CF, "kCFBooleanTrue"));
            var result = scope.arena.allocate(ADDRESS);
            var status = api.copy(query, result);
            if (status == -25300) return null;
            check(status);
            var data = scope.own(result.get(ADDRESS, 0));
            var actualType = (long) call(Native.CF, "CFGetTypeID", FunctionDescriptor.of(JAVA_LONG, ADDRESS), data);
            var dataType = (long) call(Native.CF, "CFDataGetTypeID", FunctionDescriptor.of(JAVA_LONG));
            if (actualType != dataType) throw new KeyManagerException("invalid keychain secret type");
            var length = (long) call(Native.CF, "CFDataGetLength", FunctionDescriptor.of(JAVA_LONG, ADDRESS), data);
            if (length != 32) throw new KeyManagerException("invalid keychain secret length");
            var bytes = (MemorySegment) call(Native.CF, "CFDataGetBytePtr", FunctionDescriptor.of(ADDRESS, ADDRESS), data);
            return bytes.reinterpret(length).toArray(JAVA_BYTE);
        }
    }

    @Override public void save(String id, byte[] secret) {
        Objects.requireNonNull(secret);
        if (secret.length != 32) throw new IllegalArgumentException("32-byte protection secret required");
        try (var scope = new Scope()) {
            var attributes = query(scope, id, false);
            var bytes = scope.arena.allocateFrom(JAVA_BYTE, secret);
            scope.releases.add(() -> bytes.fill((byte) 0));
            // CFDataに別の秘密コピーを残さず、呼出区間が所有する領域を参照させる。
            var data = scope.own((MemorySegment) call(Native.CF, "CFDataCreateWithBytesNoCopy",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS),
                    MemorySegment.NULL, bytes, (long) secret.length, constant(Native.CF, "kCFAllocatorNull")));
            put(attributes, "kSecValueData", data);
            check(api.add(attributes));
        }
    }

    private MemorySegment query(Scope scope, String id, boolean read) {
        var account = UUID.fromString(id).toString();
        var query = scope.own((MemorySegment) call(Native.CF, "CFDictionaryCreateMutable",
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS),
                MemorySegment.NULL, 0L, Native.CF.findOrThrow("kCFTypeDictionaryKeyCallBacks"),
                Native.CF.findOrThrow("kCFTypeDictionaryValueCallBacks")));
        put(query, "kSecClass", constant(Native.SECURITY, "kSecClassGenericPassword"));
        put(query, "kSecAttrService", scope.string(service));
        put(query, "kSecAttrAccount", scope.string(account));
        if (keychain != null) {
            var result = scope.arena.allocate(ADDRESS);
            check(api.open(scope.arena.allocateFrom(keychain.toString()), result));
            var reference = scope.own(result.get(ADDRESS, 0));
            if (read) {
                var entries = scope.arena.allocate(ADDRESS); entries.set(ADDRESS, 0, reference);
                var array = scope.own((MemorySegment) call(Native.CF, "CFArrayCreate",
                        FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS),
                        MemorySegment.NULL, entries, 1L, Native.CF.findOrThrow("kCFTypeArrayCallBacks")));
                put(query, "kSecMatchSearchList", array);
            } else put(query, "kSecUseKeychain", reference);
        }
        return query;
    }

    private static void put(MemorySegment dictionary, String name, MemorySegment value) {
        call(Native.CF, "CFDictionarySetValue", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS),
                dictionary, constant(Native.SECURITY, name), value);
    }

    private static MemorySegment constant(SymbolLookup library, String name) {
        return library.findOrThrow(name).reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
    }

    private static void check(int status) { if (status != 0) throw new NativeSecretStoreException(status); }

    private static Object call(SymbolLookup library, String name, FunctionDescriptor descriptor, Object... arguments) {
        try { return Linker.nativeLinker().downcallHandle(library.findOrThrow(name), descriptor).invokeWithArguments(arguments); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new KeyManagerException("native keychain invocation failed", failure); }
    }

    private static final class Scope implements AutoCloseable {
        final Arena arena = Arena.ofConfined();
        final ArrayList<Runnable> releases = new ArrayList<>();

        MemorySegment own(MemorySegment reference) {
            if (reference.address() == 0) throw new KeyManagerException("keychain returned no native object");
            releases.add(() -> call(Native.CF, "CFRelease", FunctionDescriptor.ofVoid(ADDRESS), reference));
            return reference;
        }

        MemorySegment string(String value) {
            return own((MemorySegment) call(Native.CF, "CFStringCreateWithCString",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT),
                    MemorySegment.NULL, arena.allocateFrom(value), 0x08000100));
        }

        @Override public void close() {
            var actions = new ArrayList<Runnable>(releases.reversed());
            releases.clear();
            actions.add(arena::close);
            CloseActions.runAll(actions.toArray(Runnable[]::new));
        }
    }

    private static final class Native {
        static final SymbolLookup CF = SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());
        static final SymbolLookup SECURITY = SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/Security.framework/Security", Arena.global());
    }

    private static final class NativeSecurityApi implements SecurityApi {
        @Override public int open(MemorySegment path, MemorySegment result) {
            return (int) call(Native.SECURITY, "SecKeychainOpen", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS), path, result);
        }

        @Override public int add(MemorySegment attributes) {
            return (int) call(Native.SECURITY, "SecItemAdd", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS), attributes, MemorySegment.NULL);
        }

        @Override public int copy(MemorySegment query, MemorySegment result) {
            return (int) call(Native.SECURITY, "SecItemCopyMatching", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS), query, result);
        }
    }
}
