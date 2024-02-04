package com.github.k7t3.tcv.vm.chat;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

class DefinedChatColors {

    private final List<Color> colors = new ArrayList<>();

    DefinedChatColors() {
        colors.add(Color.web("#FF0000"));
        colors.add(Color.web("#0000FF"));
        colors.add(Color.web("#00FF00"));
        colors.add(Color.web("#B22222"));
        colors.add(Color.web("#FF7F50"));
        colors.add(Color.web("#9ACD32"));
        colors.add(Color.web("#FF4500"));
        colors.add(Color.web("#2E8B57"));
        colors.add(Color.web("#DAA520"));
        colors.add(Color.web("#D2691E"));
        colors.add(Color.web("#5F9EA0"));
        colors.add(Color.web("#1E90FF"));
        colors.add(Color.web("#FF69B4"));
        colors.add(Color.web("#8A2BE2"));
        colors.add(Color.web("#00FF7F"));
    }

    Color getRandom() {
        var i = (int) (Math.random() * Math.max(0, colors.size() - 1));
        return colors.get(i);
    }

}
