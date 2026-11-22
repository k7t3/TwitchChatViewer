package com.github.k7t3.tcv.app.secure;

import java.util.function.Supplier;

/** 鍵管理を遅延構築して所有する。認証と保存I/Oの実完了後に終了する。 */
public final class CredentialCipherService implements AutoCloseable {
    private final KeyManagerProvider keys;

    CredentialCipherService(Supplier<KeyManager> create) { keys = new KeyManagerProvider(create); }

    public byte[] encrypt(byte[] plain) { return new CredentialCipher(keys.get()).encrypt(plain); }

    public byte[] decrypt(byte[] encrypted) { return new CredentialCipher(keys.get()).decrypt(encrypted); }

    @Override public void close() { keys.close(); }
}
