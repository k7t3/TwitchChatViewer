package com.github.k7t3.tcv.app.chat.filter;

import com.github.k7t3.tcv.domain.chat.ChatData;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 正規表現によるチャットメッセージフィルタ。
 *
 * <p>
 * {@link KeywordMessageFilter#getKeywords()}に登録される
 * 正規表現のいずれかに該当するメッセージは非表示扱いになる。
 * </p>
 */
public class KeywordMessageFilter implements ChatMessageFilter {

    public static final KeywordMessageFilter DEFAULT = new KeywordMessageFilter(List.of());

    private final CopyOnWriteArraySet<String> keywords;

    public KeywordMessageFilter(List<String> keywords) {
        this.keywords = new CopyOnWriteArraySet<>(keywords);
    }

    public Set<String> getKeywords() {
        return keywords;
    }

    @Override
    public boolean test(ChatData chatData) {
        if (keywords.isEmpty())
            return true;

        return keywords.stream()
                .filter(r -> !r.isEmpty())
                .noneMatch(r -> chatData.message().getPlain().toLowerCase().contains(r));
    }

    @Override
    public byte[] serialize() {
        try (var baos = new ByteArrayOutputStream();
             var os = new DataOutputStream(baos)) {

            for (var regex : keywords)
                os.writeUTF(regex);

            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static KeywordMessageFilter deserialize(byte[] bytes) {
        try (var bais = new ByteArrayInputStream(bytes);
             var dis = new DataInputStream(bais)) {

            var regexes = new ArrayList<String>();

            while (0 < dis.available()) {
                regexes.add(dis.readUTF());
            }

            return new KeywordMessageFilter(regexes);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
