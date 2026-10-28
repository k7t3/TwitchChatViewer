package com.github.k7t3.tcv.app.secure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretStoreProtocolTest {
    @ParameterizedTest
    @ValueSource(strings = {"magic", "version", "operation", "short-header", "short-id", "short-secret", "extra-read", "extra-save"})
    void aMalformedFrameIsRejectedBeforeTheBackendIsOpened(String fault) throws Exception {
        var frame = request(UUID.randomUUID(), fault.contains("secret") || fault.endsWith("save") ? 2 : 1,
                fault.contains("secret") || fault.endsWith("save") ? new byte[32] : null);
        frame = switch (fault) {
            case "short-header" -> Arrays.copyOf(frame, 4);
            case "short-id" -> Arrays.copyOf(frame, 21);
            case "short-secret" -> Arrays.copyOf(frame, frame.length - 1);
            case "extra-read", "extra-save" -> Arrays.copyOf(frame, frame.length + 1);
            default -> frame;
        };
        if (fault.equals("magic")) frame[0] = 0;
        if (fault.equals("version")) frame[4] = 2;
        if (fault.equals("operation")) frame[5] = 3;
        var input = new ByteArrayInputStream(frame);
        var output = new ByteArrayOutputStream();

        assertThatThrownBy(() -> SecretStoreProtocol.serve(input, output, () -> {
            throw new AssertionError("malformed input must not open OS storage");
        })).isInstanceOf(java.io.IOException.class);

        assertThat(output.size()).isZero();
    }

    @Test
    void anAbsentSecretHasAnExplicitEmptyReply() throws Exception {
        var output = new ByteArrayOutputStream();
        var store = new OsSecretStore() {
            @Override public byte[] read(String id) { return null; }
            @Override public void save(String id, byte[] secret) { throw new AssertionError("unexpected save"); }
        };

        SecretStoreProtocol.serve(new ByteArrayInputStream(request(UUID.randomUUID(), 1, null)), output, () -> store);

        assertThat(output.toByteArray()).containsExactly((byte) 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void anOsRefusalReturnsOnlyItsNumericCodeAndWipesBorrowedData(int operation) throws Exception {
        var borrowed = new AtomicReference<byte[]>();
        var calls = new AtomicInteger();
        var store = new OsSecretStore() {
            @Override public byte[] read(String id) {
                calls.incrementAndGet();
                throw new NativeSecretStoreException(-25293);
            }
            @Override public void save(String id, byte[] secret) {
                calls.incrementAndGet();
                borrowed.set(secret);
                throw new NativeSecretStoreException(-25293);
            }
        };
        var output = new ByteArrayOutputStream();
        var expected = new ByteArrayOutputStream();
        var reply = new DataOutputStream(expected);
        reply.writeByte(3);
        reply.writeInt(-25293);

        SecretStoreProtocol.serve(new ByteArrayInputStream(request(UUID.randomUUID(), operation,
                operation == 2 ? new byte[32] : null)), output, () -> store);

        assertThat(output.toByteArray()).containsExactly(expected.toByteArray());
        assertThat(calls).hasValue(1);
        if (operation == 2) assertThat(borrowed.get()).containsOnly((byte) 0);
    }

    @Test
    void aSaveBorrowsItsSecretOnlyUntilTheBackendCompletes() throws Exception {
        var id = UUID.randomUUID();
        var secret = new byte[32];
        Arrays.fill(secret, (byte) 11);
        var borrowed = new AtomicReference<byte[]>();
        var store = new OsSecretStore() {
            @Override public byte[] read(String account) { throw new AssertionError("unexpected read"); }
            @Override public void save(String account, byte[] value) {
                assertThat(account).isEqualTo(id.toString());
                assertThat(value).containsExactly(secret);
                borrowed.set(value);
            }
        };
        var output = new ByteArrayOutputStream();

        SecretStoreProtocol.serve(new ByteArrayInputStream(request(id, 2, secret)), output, () -> store);

        assertThat(output.toByteArray()).containsExactly((byte) 2);
        assertThat(borrowed.get()).containsOnly((byte) 0);
        assertThat(secret).containsOnly((byte) 11);
    }

    @Test
    void aReadReturnsTheSecretAndClearsTheBackendOwnedCopy() throws Exception {
        var id = UUID.randomUUID();
        var secret = new byte[32];
        Arrays.fill(secret, (byte) 7);
        var store = new OsSecretStore() {
            @Override public byte[] read(String account) {
                assertThat(account).isEqualTo(id.toString());
                return secret;
            }
            @Override public void save(String account, byte[] value) { throw new AssertionError("unexpected save"); }
        };
        var output = new ByteArrayOutputStream();

        SecretStoreProtocol.serve(new ByteArrayInputStream(request(id, 1, null)), output, () -> store);

        var expected = new byte[33];
        Arrays.fill(expected, (byte) 7);
        expected[0] = 1;
        assertThat(output.toByteArray()).containsExactly(expected);
        assertThat(secret).containsOnly((byte) 0);
    }

    private static byte[] request(UUID id, int operation, byte[] secret) throws Exception {
        var buffer = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(buffer)) {
            data.writeInt(0x5443564b);
            data.writeByte(1);
            data.writeByte(operation);
            data.writeLong(id.getMostSignificantBits());
            data.writeLong(id.getLeastSignificantBits());
            if (secret != null) data.write(secret);
        }
        return buffer.toByteArray();
    }
}
