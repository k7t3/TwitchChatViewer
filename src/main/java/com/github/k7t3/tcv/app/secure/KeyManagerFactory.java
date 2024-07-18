package com.github.k7t3.tcv.app.secure;

import com.github.k7t3.tcv.app.core.OS;

import java.nio.file.Path;

public class KeyManagerFactory {

    static final String DEFAULT_PATH = System.getProperty(
            "key.manager.path",
            OS.current().getApplicationDirectory().resolve("keymanager.ks").toString()
    );
    static final String DEFAULT_PASSWORD = System.getProperty("key.manager.password", "changeit");

    private KeyManagerFactory() {
    }

    public static KeyManager getInstance() {
        return Holder.KEY_MANAGER;
    }

    private static final class Holder {
        private static final KeyManager KEY_MANAGER;
        static {
            KEY_MANAGER = new KeyManagerImpl(Path.of(DEFAULT_PATH), DEFAULT_PASSWORD.toCharArray());
        }
    }

}
