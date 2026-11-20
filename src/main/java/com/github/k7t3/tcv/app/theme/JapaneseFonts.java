package com.github.k7t3.tcv.app.theme;

import javafx.scene.text.Font;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 画面とチャットの既定書体を、JavaFX が利用できるフォントから選ぶ。 */
public final class JapaneseFonts {

    private static final String HIRAGINO = "Hiragino Sans";

    private JapaneseFonts() {}

    /** ヒラギノ角ゴシックが利用できる場合はそのファミリー名を、それ以外は JavaFX の既定書体名を返す。 */
    public static String defaultDisplayFamily() {
        return selectHiragino(Font.getFamilies()).orElseGet(() -> Font.getDefault().getFamily());
    }

    static Optional<String> selectHiragino(List<String> families) {
        return families.contains(HIRAGINO) ? Optional.of(HIRAGINO) : Optional.empty();
    }

    static String themeStylesheet(String themeStylesheet) {
        var displayFamily = selectHiragino(Font.getFamilies());
        if (displayFamily.isEmpty()) return themeStylesheet;

        var themeUrl = Objects.requireNonNull(JapaneseFonts.class.getResource(themeStylesheet), themeStylesheet);
        // JavaFX 27.0.1 ではバイナリ CSS の @import に失敗するため、フラグメントを付けてテキスト版を読む。
        var css = """
                @import "%s#stylesheet";
                .root { -fx-font-family: "%s"; }
                """.formatted(themeUrl.toExternalForm(), displayFamily.get());
        return "data:text/css;charset=UTF-8," + URLEncoder.encode(css, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
