package com.github.k7t3.tcv.app.secure;

import com.github.k7t3.tcv.lifecycle.CloseActions;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import static java.lang.foreign.ValueLayout.*;

/** Windowsのユーザー別Credential Managerに鍵保護用の秘密を保存する。 */
final class WindowsCredentialSecretStore implements OsSecretStore {
    private final CredentialApi api;
    /** 0は成功、非0は呼出直後に捕捉したWin32エラー。成功したreadのbufferは呼出元がfreeする。 */
    interface CredentialApi {
        int read(MemorySegment target, MemorySegment result);
        int write(MemorySegment credential);
        void free(MemorySegment credential);
    }

    WindowsCredentialSecretStore() { this(new NativeCredentialApi()); }

    WindowsCredentialSecretStore(CredentialApi api) {
        this.api = Objects.requireNonNull(api);
        if (ADDRESS.byteSize() != 8) throw new KeyManagerException("64-bit Windows credential layout required");
    }

    @Override public byte[] read(String id) {
        try (var arena = Arena.ofConfined()) {
            var result = arena.allocate(ADDRESS);
            var status = api.read(target(arena, id), result);
            if (status == 1168) return null;
            if (status != 0) throw new NativeSecretStoreException(status);
            var pointer = result.get(ADDRESS, 0);
            if (pointer.equals(MemorySegment.NULL)) throw new KeyManagerException("missing native credential buffer");
            try (var owned = new CredentialBuffer(pointer)) {
                var bytes = owned.bytes();
                if (owned.value.get(JAVA_INT, 4) != 1 || bytes.byteSize() != 32)
                    throw new KeyManagerException("invalid native credential type or length");
                return bytes.toArray(JAVA_BYTE);
            }
        }
    }
    @Override public void save(String id, byte[] secret) {
        Objects.requireNonNull(secret);
        if (secret.length != 32) throw new IllegalArgumentException("32-byte protection secret required");
        try (var arena = Arena.ofConfined()) {
            var name = target(arena, id);
            var bytes = arena.allocateFrom(JAVA_BYTE, secret);
            try {
                // 64-bit CREDENTIALW。BlobSizeとBlobポインターの間に4バイトのpaddingがある。
                var credential = arena.allocate(80, 8);
                credential.set(JAVA_INT, 4, 1);
                credential.set(ADDRESS, 8, name);
                credential.set(JAVA_INT, 32, 32);
                credential.set(ADDRESS, 40, bytes);
                credential.set(JAVA_INT, 48, 2);
                var status = api.write(credential);
                if (status != 0) throw new NativeSecretStoreException(status);
            } finally { bytes.fill((byte) 0); }
        }
    }

    private static MemorySegment target(Arena arena, String id) {
        var name = "com.github.k7t3.tcv/key-protection/" + UUID.fromString(id);
        return arena.allocateFrom(name, StandardCharsets.UTF_16LE);
    }

    private final class CredentialBuffer implements AutoCloseable {
        private final MemorySegment value;

        private CredentialBuffer(MemorySegment pointer) { value = pointer.reinterpret(80); }

        private MemorySegment bytes() {
            var length = value.get(JAVA_INT, 32);
            var pointer = value.get(ADDRESS, 40);
            if (length == 0) return MemorySegment.NULL;
            if (length < 0 || length > 2560 || pointer.equals(MemorySegment.NULL))
                throw new KeyManagerException("invalid native credential blob");
            return pointer.reinterpret(length);
        }

        @Override public void close() {
            CloseActions.runAll(() -> bytes().fill((byte) 0), () -> api.free(value));
        }
    }

    static final class NativeCredentialApi implements CredentialApi {
        private final SymbolLookup symbols;
        private final String errorName;

        private NativeCredentialApi() { this(name -> Native.LIBRARY.find(name), "GetLastError"); }

        NativeCredentialApi(SymbolLookup symbols, String errorName) {
            this.symbols = Objects.requireNonNull(symbols);
            this.errorName = Objects.requireNonNull(errorName);
        }

        @Override public int read(MemorySegment target, MemorySegment result) {
            return status("CredReadW", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS), target, 1, 0, result);
        }

        @Override public int write(MemorySegment credential) {
            return status("CredWriteW", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT), credential, 0);
        }

        @Override public void free(MemorySegment credential) {
            var handle = Linker.nativeLinker().downcallHandle(symbols.findOrThrow("CredFree"), FunctionDescriptor.ofVoid(ADDRESS));
            invoke(handle, List.of(credential));
        }

        private int status(String name, FunctionDescriptor descriptor, Object... arguments) {
            // GetLastErrorを後から別呼出で読むとJVMに上書きされ得るため、downcall直後の状態を捕捉する。
            var handle = Linker.nativeLinker().downcallHandle(symbols.findOrThrow(name), descriptor, Linker.Option.captureCallState(errorName));
            var layout = Linker.Option.captureStateLayout();
            try (var arena = Arena.ofConfined()) {
                var state = arena.allocate(layout);
                var values = new ArrayList<Object>();
                values.add(state);
                values.addAll(List.of(arguments));
                if ((int) invoke(handle, values) != 0) return 0;
                var code = state.get(JAVA_INT, layout.byteOffset(MemoryLayout.PathElement.groupElement(errorName)));
                return code == 0 ? -1 : code;
            }
        }

        private static Object invoke(MethodHandle handle, List<Object> arguments) {
            try { return handle.invokeWithArguments(arguments); }
            catch (RuntimeException | Error failure) { throw failure; }
            catch (Throwable failure) { throw new KeyManagerException("OS credential function failed", failure); }
        }
    }

    private static final class Native {
        private static final SymbolLookup LIBRARY = systemLibrary();

        private static SymbolLookup systemLibrary() {
            var root = System.getenv("SystemRoot");
            if (root == null || root.isBlank()) throw new KeyManagerException("Windows system directory unavailable");
            return SymbolLookup.libraryLookup(Path.of(root, "System32", "Advapi32.dll"), Arena.global());
        }
    }
}
