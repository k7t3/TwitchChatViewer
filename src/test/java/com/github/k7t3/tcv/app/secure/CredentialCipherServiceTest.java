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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialCipherServiceTest {

    @Test
    void anEmptyCredentialListCanBeEncryptedAndEachWriteHasADistinctRepresentation() {
        var empty = new byte[0];
        var first = cipher.encrypt(empty);
        var second = cipher.encrypt(empty);

        assertThat(cipher.decrypt(first)).containsExactly(empty);
        assertThat(cipher.decrypt(second)).containsExactly(empty);
        assertThat(Arrays.equals(first, second)).isFalse();
    }

    @TempDir Path directory;
    private KeyManagerImpl manager;
    private CredentialCipherService cipher;

    @BeforeEach
    void setUp() {
        manager = new KeyManagerImpl(directory.resolve("keys.p12"), "test-password".toCharArray());
        cipher = new CredentialCipherService(() -> manager);
    }

    @AfterEach
    void tearDown() {
        try { cipher.close(); }
        finally { manager.close(); }
    }

    @Test
    void binaryPayloadRoundTripsThroughTheDefaultFacade() throws IOException {
        // テストデータを生成
        var message = "serialize";
        var baos = new ByteArrayOutputStream();
        try (baos; var dos = new DataOutputStream(baos)) {
            dos.writeInt(message.length());
            dos.writeUTF(message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var plain = baos.toByteArray();

        var enc = cipher.encrypt(plain);
        var restore = cipher.decrypt(enc);

        try (var bais = new ByteArrayInputStream(restore);
             var dis = new DataInputStream(bais)) {
            assertThat(dis.readInt()).isEqualTo(message.length());
            assertThat(dis.readUTF()).isEqualTo(message);
        }
    }
}
