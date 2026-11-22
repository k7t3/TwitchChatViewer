package com.github.k7t3.tcv.app.secure;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static java.lang.foreign.ValueLayout.*;

/** 実ユーザーの秘密を照会せず、専用キーチェーンだけでネイティブ境界を検証する。 */
public final class MacKeychainProcessProbe {
    private static final SymbolLookup SECURITY = SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/Security.framework/Security", Arena.global());
    private static final SymbolLookup CF = SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());

    /** @param arguments create/delete/serveと、テスト専用キーチェーンの絶対パス */
    public static void main(String[] arguments) throws Throwable {
        var mode = arguments[0];
        var path = Path.of(arguments[1]);
        if (mode.equals("serve")) {
            SecretStoreProtocol.serve(System.in, System.out,
                    () -> new MacKeychainSecretStore("com.github.k7t3.tcv.test.keys", path));
            return;
        }
        try (var arena = Arena.ofConfined()) {
            var result = arena.allocate(ADDRESS);
            var metadata = path.resolveSibling("keychain-search-list.txt");
            if (mode.equals("create")) {
                Files.write(metadata, searchList(arena));
                var password = arena.allocateFrom("synthetic-keychain-fixture");
                status((int) call(SECURITY, "SecKeychainCreate",
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_BYTE, ADDRESS, ADDRESS),
                        arena.allocateFrom(path.toString()), 26, password, (byte) 0, MemorySegment.NULL, result));
                release(result.get(ADDRESS, 0));
            } else if (mode.equals("delete")) {
                if (Files.exists(path)) {
                    status((int) call(SECURITY, "SecKeychainOpen", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS),
                            arena.allocateFrom(path.toString()), result));
                    var keychain = result.get(ADDRESS, 0);
                    try { status((int) call(SECURITY, "SecKeychainDelete", FunctionDescriptor.of(JAVA_INT, ADDRESS), keychain)); }
                    finally { release(keychain); }
                }
                if (Files.exists(metadata)) {
                    if (!Files.readAllLines(metadata).equals(searchList(arena))) throw new AssertionError("keychain search list changed");
                    Files.writeString(path.resolveSibling("keychain-isolation.ok"), "search list preserved");
                }
            } else throw new AssertionError("unknown fixture mode");
        }
    }

    private static java.util.List<String> searchList(Arena arena) throws Throwable {
        var result = arena.allocate(ADDRESS);
        status((int) call(SECURITY, "SecKeychainCopySearchList", FunctionDescriptor.of(JAVA_INT, ADDRESS), result));
        var list = result.get(ADDRESS, 0);
        try {
            var count = (long) call(CF, "CFArrayGetCount", FunctionDescriptor.of(JAVA_LONG, ADDRESS), list);
            var paths = new ArrayList<String>();
            for (long i = 0; i < count; i++) {
                var keychain = (MemorySegment) call(CF, "CFArrayGetValueAtIndex", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG), list, i);
                var size = arena.allocate(JAVA_INT); size.set(JAVA_INT, 0, 4096);
                var path = arena.allocate(4096);
                status((int) call(SECURITY, "SecKeychainGetPath", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS), keychain, size, path));
                paths.add(path.getString(0));
            }
            return paths.stream().sorted().toList();
        } finally { release(list); }
    }

    private static void release(MemorySegment value) throws Throwable {
        call(CF, "CFRelease", FunctionDescriptor.ofVoid(ADDRESS), value);
    }

    private static void status(int value) { if (value != 0) throw new AssertionError("keychain fixture status " + value); }

    private static Object call(SymbolLookup library, String name, FunctionDescriptor descriptor, Object... arguments) throws Throwable {
        return Linker.nativeLinker().downcallHandle(library.findOrThrow(name), descriptor).invokeWithArguments(arguments);
    }
}
