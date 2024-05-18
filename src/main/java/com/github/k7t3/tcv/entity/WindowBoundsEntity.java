package com.github.k7t3.tcv.entity;

public record WindowBoundsEntity(
        String identity,
        double x,
        double y,
        double width,
        double height,
        boolean maximized
) {

    public static final WindowBoundsEntity DEFAULT = new WindowBoundsEntity(
            null,
            Double.NaN,
            Double.NaN,
            1024,
            768,
            false
    );

}
