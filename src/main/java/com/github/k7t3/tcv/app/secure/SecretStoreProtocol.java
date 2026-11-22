package com.github.k7t3.tcv.app.secure;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

/** 一回限りの私有パイプでOS保管ヘルパーの要求を受け付ける。 */
final class SecretStoreProtocol {
    private SecretStoreProtocol() {}

    static void serve(InputStream input, OutputStream output, Supplier<OsSecretStore> store) throws IOException {
        var request = new DataInputStream(input);
        var reply = new DataOutputStream(output);
        if (request.readInt() != 0x5443564b || request.readUnsignedByte() != 1)
            throw new IOException("invalid OS helper request");
        var operation = request.readUnsignedByte();
        if (operation != 1 && operation != 2) throw new IOException("invalid OS helper operation");
        var id = new UUID(request.readLong(), request.readLong()).toString();
        byte[] secret = operation == 2 ? new byte[32] : null;
        try {
            if (operation == 2) request.readFully(secret);
            if (request.read() != -1) throw new IOException("unexpected trailing OS helper request");
            var backend = store.get();
            if (operation == 2) {
                backend.save(id, secret);
                reply.writeByte(2);
            } else {
                secret = backend.read(id);
                if (secret == null) reply.writeByte(0);
                else {
                    if (secret.length != 32) throw new IOException("invalid OS helper secret length");
                    reply.writeByte(1);
                    reply.write(secret);
                }
            }
        } catch (NativeSecretStoreException failure) {
            reply.writeByte(3);
            reply.writeInt(failure.code());
        } finally { if (secret != null) Arrays.fill(secret, (byte) 0); }
        reply.flush();
    }
}
