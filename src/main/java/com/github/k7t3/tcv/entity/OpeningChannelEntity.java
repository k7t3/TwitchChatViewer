package com.github.k7t3.tcv.entity;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record OpeningChannelEntity(
        String userId,
        int order,
        String windowId
) {
}
