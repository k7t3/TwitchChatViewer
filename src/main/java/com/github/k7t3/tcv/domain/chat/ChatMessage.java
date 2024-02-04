package com.github.k7t3.tcv.domain.chat;

import java.util.AbstractList;
import java.util.List;

public class ChatMessage extends AbstractList<ChatMessage.MessageFragment> {

    public enum Type { EMOTE, MESSAGE }

    public record MessageFragment(Type type, String fragment) {}

    private final List<MessageFragment> fragments;

    public ChatMessage(List<MessageFragment> fragments) {
        this.fragments = fragments;
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
