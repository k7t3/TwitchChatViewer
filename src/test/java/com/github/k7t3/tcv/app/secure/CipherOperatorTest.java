package com.github.k7t3.tcv.app.secure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CipherOperatorTest {

    private String storePath;

    @BeforeAll
    static void setUpAll() throws IOException {
        var temp = Files.createTempFile(null, null);
        Files.delete(temp); // ファイルの実態は不要なので削除
        // キーマネージャ用のファイルパスを設定
        System.setProperty("key.manager.path", temp.toAbsolutePath().toString());
    }

    @BeforeEach
    void setUp() {
        // DEFAULT_PATH はテスト用のプロパティで上書きすること
        storePath = KeyManagerFactory.DEFAULT_PATH;
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(Path.of(storePath));
    }

    @Test
    void test() throws IOException {
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


        var enc = CipherOperator.encrypt(plain);
        var restore = CipherOperator.decrypt(enc);

        try (var bais = new ByteArrayInputStream(restore);
             var dis = new DataInputStream(bais)) {
            assertEquals(message.length(), dis.readInt());
            assertEquals(message, dis.readUTF());
        }
    }
}