package com.github.k7t3.tcv.support;

import java.util.List;
import java.util.ArrayList;

/** 子JVMの検証ログを回収するテスト用の起動入口。 */
public final class TestJvm {
    private TestJvm() {}

    public static ProcessBuilder utf8(String... command) { return utf8(List.of(command)); }

    public static ProcessBuilder utf8(List<String> command) {
        var arguments = new ArrayList<>(command);
        // Files.readStringで回収するログは、OSのコンソール文字コードから独立させる。
        arguments.addAll(1, List.of("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"));
        return new ProcessBuilder(arguments);
    }
}
