package com.github.k7t3.tcv.app.secure;

/** 秘密やOS由来の自由文を含めず、拒否・障害の数値コードを保持する。 */
final class NativeSecretStoreException extends KeyManagerException {
    private final int code;

    NativeSecretStoreException(int code) {
        super("OS secret store failed with code " + code);
        this.code = code;
    }

    int code() { return code; }
}
