package com.github.k7t3.tcv.app.secure;

/**
 * KeyStoreに関する例外
 */
@SuppressWarnings("unused")
public class KeyManagerException extends RuntimeException {

    public KeyManagerException() {
    }

    public KeyManagerException(String message) {
        super(message);
    }

    public KeyManagerException(String message, Throwable cause) {
        super(message, cause);
    }

    public KeyManagerException(Throwable cause) {
        super(cause);
    }

    public KeyManagerException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
