package com.github.k7t3.tcv.domain.chat;

import java.util.AbstractList;
import java.util.List;
import java.util.stream.Collectors;

public class ChatMessage extends AbstractList<ChatMessage.MessageFragment> {

    public enum Type { EMOTE, MESSAGE }

    public record MessageFragment(Type type, String fragment) {}

    private final List<MessageFragment> fragments;

    private final String plain;

    public ChatMessage(List<MessageFragment> fragments) {
        this.fragments = fragments;
        plain = fragments.stream().map(MessageFragment::fragment).collect(Collectors.joining());
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
