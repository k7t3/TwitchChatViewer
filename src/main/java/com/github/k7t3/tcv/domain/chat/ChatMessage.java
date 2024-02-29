package com.github.k7t3.tcv.domain.chat;

import java.util.AbstractList;
import java.util.List;

public class ChatMessage extends AbstractList<ChatMessage.MessageFragment> {

    public enum Type { EMOTE, MESSAGE }

    public record MessageFragment(Type type, String fragment) {}

    private final List<MessageFragment> fragments;

    private final String plain;

    public ChatMessage(String plain, List<MessageFragment> fragments) {
        this.plain = plain;
        this.fragments = fragments;
    }

    public String getPlain() {
        return plain;
    }

    @Override
    public MessageFragment get(int index) {
        return fragments.get(index);
    }

    @Override
    public int size() {
        return fragments.size();
    }

}
