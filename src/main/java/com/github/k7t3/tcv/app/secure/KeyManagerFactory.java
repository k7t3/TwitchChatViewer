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

package com.github.k7t3.tcv.app.secure;

import com.github.k7t3.tcv.app.core.OS;

import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.Arrays;
import java.time.Duration;

/** OS保護または明示パスワードの鍵管理を、暗号化サービスの所有範囲へ遅延構築する。 */
public class KeyManagerFactory {

    static final String DEFAULT_PATH = System.getProperty(
            "key.manager.path",
            OS.current().getApplicationDirectory().resolve("keymanager.ks").toString()
    );

    private KeyManagerFactory() {
    }

    /** OS保護を既定とし、key.manager.passwordの明示指定だけ従来方式を使用する。 */
    public static CredentialCipherService create() {
        return create(Path.of(DEFAULT_PATH), System.getProperty("key.manager.password"),
                () -> new ProcessOsSecretStore(SecretStoreCommand::current, Duration.ofSeconds(30)));
    }

    static CredentialCipherService create(Path path, String explicitPassword, Supplier<OsSecretStore> osStore) {
        return new CredentialCipherService(() -> {
            if (explicitPassword == null) return new KeyManagerImpl(path, osStore.get());
            var password = explicitPassword.toCharArray();
            try { return new KeyManagerImpl(path, password); }
            finally { Arrays.fill(password, '\0'); }
        });
    }

}
