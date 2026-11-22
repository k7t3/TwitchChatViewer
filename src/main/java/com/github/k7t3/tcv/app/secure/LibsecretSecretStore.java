package com.github.k7t3.tcv.app.secure;

import com.github.k7t3.tcv.lifecycle.CloseActions;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import static java.lang.foreign.ValueLayout.*;

/** Linux Secret Serviceのbinary valueを操作単位で所有する。 */
final class LibsecretSecretStore implements OsSecretStore {
    private final SecretApi api;
    /** attributes/value/errorの所有権は呼出元へ移り、dataはvalueからの借用となる。 */
    interface SecretApi {
        MemorySegment attributes(MemorySegment application, MemorySegment id);
        MemorySegment read(MemorySegment attributes, MemorySegment error);
        MemorySegment data(MemorySegment value, MemorySegment length);
        MemorySegment value(MemorySegment secret, long length);
        int save(MemorySegment attributes, MemorySegment value, MemorySegment error);
        void releaseAttributes(MemorySegment attributes);
        void releaseValue(MemorySegment value);
        void releaseError(MemorySegment error);
    }

    LibsecretSecretStore() {
        this(new NativeSecretApi(name -> NativeLibraries.SECRET.find(name), name -> NativeLibraries.GLIB.find(name)));
    }

    LibsecretSecretStore(SecretApi api) {
        this.api = Objects.requireNonNull(api);
        if (ADDRESS.byteSize() != 8) throw new KeyManagerException("64-bit libsecret layout required");
    }

    @Override public byte[] read(String id) {
        try (var scope = new Scope()) {
            var attributes = attributes(scope, id);
            var error = scope.arena.allocate(ADDRESS);
            var value = api.read(attributes, error);
            if (!value.equals(MemorySegment.NULL)) scope.own(value, () -> api.releaseValue(value));
            checkError(scope, error);
            if (value.equals(MemorySegment.NULL)) return null;
            var length = scope.arena.allocate(JAVA_LONG);
            var bytes = api.data(value, length);
            if (length.get(JAVA_LONG, 0) != 32 || bytes.equals(MemorySegment.NULL))
                throw new KeyManagerException("invalid libsecret secret length");
            return bytes.reinterpret(32).toArray(JAVA_BYTE);
        }
    }
    @Override public void save(String id, byte[] secret) {
        Objects.requireNonNull(secret);
        if (secret.length != 32) throw new IllegalArgumentException("32-byte protection secret required");
        try (var scope = new Scope()) {
            var attributes = attributes(scope, id);
            var bytes = scope.arena.allocateFrom(JAVA_BYTE, secret);
            scope.releases.add(() -> bytes.fill((byte) 0));
            var value = api.value(bytes, 32);
            scope.own(value, () -> api.releaseValue(value));
            var error = scope.arena.allocate(ADDRESS);
            var saved = api.save(attributes, value, error);
            checkError(scope, error);
            if (saved == 0) throw new NativeSecretStoreException(-1);
        }
    }

    private MemorySegment attributes(Scope scope, String id) {
        var account = UUID.fromString(id).toString();
        var attributes = api.attributes(scope.arena.allocateFrom("com.github.k7t3.tcv"), scope.arena.allocateFrom(account));
        return scope.own(attributes, () -> api.releaseAttributes(attributes));
    }

    private void checkError(Scope scope, MemorySegment result) {
        var error = result.get(ADDRESS, 0);
        if (error.equals(MemorySegment.NULL)) return;
        scope.own(error, () -> api.releaseError(error));
        var code = error.reinterpret(8).get(JAVA_INT, 4);
        throw new NativeSecretStoreException(code == 0 ? -1 : code);
    }

    private static final class Scope implements AutoCloseable {
        private final Arena arena = Arena.ofConfined();
        private final ArrayList<Runnable> releases = new ArrayList<>();

        private MemorySegment own(MemorySegment value, Runnable release) {
            if (value.equals(MemorySegment.NULL)) throw new KeyManagerException("missing libsecret resource");
            releases.add(release);
            return value;
        }

        @Override public void close() {
            var actions = new ArrayList<>(releases.reversed());
            actions.add(arena::close);
            CloseActions.runAll(actions.toArray(Runnable[]::new));
        }
    }

    static final class NativeSecretApi implements SecretApi {
        // HashTableは文字列を借用する。公開固定キーはテーブルより長く保持し、動的な値はScopeが所有する。
        private static final MemorySegment APPLICATION_KEY = Arena.global().allocateFrom("application");
        private static final MemorySegment ID_KEY = Arena.global().allocateFrom("key-id");
        private static final MemorySegment CONTENT_TYPE = Arena.global().allocateFrom("application/octet-stream");
        private static final MemorySegment LABEL = Arena.global().allocateFrom("Twitch Chat Viewer key protection");
        private final SymbolLookup secret;
        private final SymbolLookup glib;

        NativeSecretApi(SymbolLookup secret, SymbolLookup glib) {
            this.secret = Objects.requireNonNull(secret);
            this.glib = Objects.requireNonNull(glib);
        }

        @Override public MemorySegment attributes(MemorySegment application, MemorySegment id) {
            var table = (MemorySegment) call(glib, "g_hash_table_new", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS),
                    glib.findOrThrow("g_str_hash"), glib.findOrThrow("g_str_equal"));
            if (table.equals(MemorySegment.NULL)) throw new KeyManagerException("missing libsecret attributes");
            var completed = false;
            try {
                insert(table, APPLICATION_KEY, application);
                insert(table, ID_KEY, id);
                completed = true;
                return table;
            } finally { if (!completed) releaseAttributes(table); }
        }

        private void insert(MemorySegment table, MemorySegment key, MemorySegment value) {
            if ((int) call(glib, "g_hash_table_insert", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS), table, key, value) == 0)
                throw new KeyManagerException("libsecret attribute insertion failed");
        }

        @Override public MemorySegment read(MemorySegment attributes, MemorySegment error) {
            return (MemorySegment) call(secret, "secret_password_lookupv_binary_sync",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
                    MemorySegment.NULL, attributes, MemorySegment.NULL, error);
        }

        @Override public MemorySegment data(MemorySegment value, MemorySegment length) {
            return (MemorySegment) call(secret, "secret_value_get", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS), value, length);
        }

        @Override public MemorySegment value(MemorySegment bytes, long length) {
            return (MemorySegment) call(secret, "secret_value_new", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, ADDRESS),
                    bytes, length, CONTENT_TYPE);
        }

        @Override public int save(MemorySegment attributes, MemorySegment value, MemorySegment error) {
            // NULL collectionは次のログインにも残る既定保管先。session collectionへ退避しない。
            return (int) call(secret, "secret_password_storev_binary_sync",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
                    MemorySegment.NULL, attributes, MemorySegment.NULL, LABEL, value, MemorySegment.NULL, error);
        }

        @Override public void releaseAttributes(MemorySegment attributes) {
            call(glib, "g_hash_table_unref", FunctionDescriptor.ofVoid(ADDRESS), attributes);
        }

        @Override public void releaseValue(MemorySegment value) {
            call(secret, "secret_value_unref", FunctionDescriptor.ofVoid(ADDRESS), value);
        }

        @Override public void releaseError(MemorySegment error) {
            call(glib, "g_error_free", FunctionDescriptor.ofVoid(ADDRESS), error);
        }

        private static Object call(SymbolLookup library, String name, FunctionDescriptor descriptor, Object... arguments) {
            try { return Linker.nativeLinker().downcallHandle(library.findOrThrow(name), descriptor).invokeWithArguments(arguments); }
            catch (RuntimeException | Error failure) { throw failure; }
            catch (Throwable failure) { throw new KeyManagerException("libsecret function failed", failure); }
        }
    }

    private static final class NativeLibraries {
        private static final SymbolLookup SECRET = SymbolLookup.libraryLookup("libsecret-1.so.0", Arena.global());
        private static final SymbolLookup GLIB = SymbolLookup.libraryLookup("libglib-2.0.so.0", Arena.global());
    }
}
