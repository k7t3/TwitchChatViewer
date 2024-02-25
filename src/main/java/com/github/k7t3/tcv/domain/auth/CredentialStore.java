package com.github.k7t3.tcv.domain.auth;

import com.github.philippheuer.credentialmanager.api.IStorageBackend;

public interface CredentialStore extends IStorageBackend {

    /**
     * 保存されている資格情報をクリアする
     */
    void clearCredentials();

}
