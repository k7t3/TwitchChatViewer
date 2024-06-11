package com.github.k7t3.tcv.reactive;

/**
 * 流量制限ポリシー
 */
public enum BackPressurePolicy {

    /**
     * 制限しない
     */
    FULL(Long.MAX_VALUE),

    /**
     * 128アイテムでリクエストする
     */
    BALANCE(128),

    /**
     * アイテムを一つずつリクエストする
     */
    MINIMUM(1);

    private final long segmentSize;

    BackPressurePolicy(long segmentSize) {
        this.segmentSize = segmentSize;
    }

    public long getSegmentSize() {
        return segmentSize;
    }
}
