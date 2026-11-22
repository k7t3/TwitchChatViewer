/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.domain.auth;

import com.github.philippheuer.credentialmanager.api.IStorageBackend;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.credentialmanager.domain.Credential;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public interface CredentialStore extends IStorageBackend {

    record Snapshot(String revision, List<Credential> credentials) {
        public Snapshot { credentials = List.copyOf(credentials); }
    }

    Snapshot snapshot();

    /** 比較と許可判定と保存を同じ排他区間で行い、変更した版を返す。 */
    Optional<Snapshot> compareAndSet(Snapshot expected, List<Credential> replacement, BooleanSupplier permitted);

    /**
     * 保存されている資格情報をクリアする
     */
    void clearCredentials();

    /** 現在の保存値に同じ provider / access token がある場合だけ、その資格情報を削除する。 */
    boolean removeCredential(OAuth2Credential expected);

}
