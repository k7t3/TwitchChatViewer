package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.domain.chat.ChatData;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class RegexChatMessageFilter implements ChatMessageFilter {

    public static final RegexChatMessageFilter DEFAULT = new RegexChatMessageFilter(List.of());

    private final CopyOnWriteArraySet<String> regexes;

    public RegexChatMessageFilter(List<String> regexes) {
        this.regexes = new CopyOnWriteArraySet<>(regexes);
    }

    public Set<String> getRegexes() {
        return regexes;
    }

    @Override
    public boolean test(ChatData chatData) {
        if (regexes.isEmpty())
            return true;

        return regexes.stream()
                .filter(r -> !r.isEmpty())
                .map(this::regex)
                .noneMatch(r -> chatData.message().getPlain().matches(r));
    }

    private String regex(String r) {
        var regex = r;

        if (!r.startsWith("^"))
            regex = ".*" + regex;

        if (!r.endsWith("$"))
            regex = regex + ".*";

        return regex;
    }

    @Override
    public byte[] serialize() {
        try (var baos = new ByteArrayOutputStream();
             var os = new DataOutputStream(baos)) {

            for (var regex : regexes)
                os.writeUTF(regex);

            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static RegexChatMessageFilter deserialize(byte[] bytes) {
        try (var bais = new ByteArrayInputStream(bytes);
             var dis = new DataInputStream(bais)) {

            var regexes = new ArrayList<String>();

            while (0 < dis.available()) {
                regexes.add(dis.readUTF());
            }

            return new RegexChatMessageFilter(regexes);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
