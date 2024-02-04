package com.github.k7t3.tcv.domain.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ChatMessageParser {

    private record Emote(String id, int begin, int end) {}

    private final Comparator<Emote> emoteComparator = Comparator.comparingInt(Emote::begin);

    public ChatMessage parse(String message, String emoteInfo) {
        if (message == null || message.isEmpty() || emoteInfo == null || emoteInfo.isEmpty()) {
            return new ChatMessage(List.of(new ChatMessage.MessageFragment(ChatMessage.Type.MESSAGE, message)));
        }

        var emotes = parseEmotes(emoteInfo);

        // サロゲートペアを考慮して書記素クラスタを使って文字列を分割
        var chars = message.split("\\b{g}");
        var length = chars.length;

        var list = new ArrayList<ChatMessage.MessageFragment>();

        int cursor = 0;

        for (var emote : emotes) {
            var begin = emote.begin();
            var end = emote.end();

            // 次のエモートまでの間の文字の残り
            int remain = begin - cursor;

            // エモートまでの文字を文字列とする
            if (0 < remain) {
                var builder = new StringBuilder();
                for (var c = cursor; c < begin; c++) {
                    builder.append(chars[c]);
                }
                list.add(new ChatMessage.MessageFragment(ChatMessage.Type.MESSAGE, builder.toString()));
            }

            list.add(new ChatMessage.MessageFragment(ChatMessage.Type.EMOTE, emote.id()));

            cursor = end + 1;
        }

        if (cursor < length) {
            var builder = new StringBuilder();
            for (var c = cursor; c < length; c++) {
                builder.append(chars[c]);
            }
            list.add(new ChatMessage.MessageFragment(ChatMessage.Type.MESSAGE, builder.toString()));
        }

        return new ChatMessage(list);
    }

    private List<Emote> parseEmotes(String emoteValue) {
        var result = new ArrayList<Emote>();
        var emotes = emoteValue.split("/");
        for (var emote : emotes) {
            var emoteSplit = emote.split(":", 2);
            var id = emoteSplit[0];
            var ranges = emoteSplit[1].split(",");
            for (var range : ranges) {
                var rangeSplit = range.split("-", 2);
                var begin = Integer.parseInt(rangeSplit[0]);
                var end = Integer.parseInt(rangeSplit[1]);
                result.add(new Emote(id, begin, end));
            }
        }
        result.sort(emoteComparator);
        return result;
    }
}
