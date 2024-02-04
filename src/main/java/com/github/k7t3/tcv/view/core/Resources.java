package com.github.k7t3.tcv.view.core;

import java.util.ResourceBundle;

public class Resources {

    private static final ResourceBundle RESOURCE_BUNDLE;

    static {
        RESOURCE_BUNDLE = ResourceBundle.getBundle("com.github.k7t3.tcv.tcv");
    }

    public static ResourceBundle getResourceBundle() {
        return RESOURCE_BUNDLE;
    }

    public static String getString(String key) {
        return RESOURCE_BUNDLE.getString(key);
    }

}
