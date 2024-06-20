package com.github.k7t3.tcv.domain.chat;

import java.util.AbstractList;
import java.util.List;

public class ChatMessage extends AbstractList<ChatMessageFragment> {

    private final List<ChatMessageFragment> fragments;

    private final String plain;

    public ChatMessage(String plain, List<ChatMessageFragment> fragments) {
        this.plain = plain;
        this.fragments = fragments;
    }

    public String getPlain() {
        return plain;
    }

    @Override
    public ChatMessageFragment get(int index) {
        return fragments.get(index);
    }

    @Override
    public int size() {
        return fragments.size();
    }

}
