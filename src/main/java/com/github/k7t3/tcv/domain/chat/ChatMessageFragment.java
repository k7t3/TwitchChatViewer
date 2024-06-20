package com.github.k7t3.tcv.domain.chat;

/**
 * チャットメッセージを構成する部品。
 * @param type 部品が表す種類
 * @param text メッセージのテキスト
 * @param additional 部品に付属する追加情報。
 *                   エモートの場合は識別子を表し、絵文字のときはHexコードを表す。
 */
public record ChatMessageFragment(
        Type type,
        String text,
        String additional
) {

    public static ChatMessageFragment text(String text) {
        return new ChatMessageFragment(Type.TEXT, text, null);
    }

    public static ChatMessageFragment emote(String emote, String name) {
        return new ChatMessageFragment(Type.EMOTE, emote, name);
    }

    public static ChatMessageFragment emoji(String emojiText, String emojiHexCode) {
        return new ChatMessageFragment(Type.EMOJI, emojiText, emojiHexCode);
    }

    public enum Type {
        EMOTE, EMOJI, TEXT
    }

}
