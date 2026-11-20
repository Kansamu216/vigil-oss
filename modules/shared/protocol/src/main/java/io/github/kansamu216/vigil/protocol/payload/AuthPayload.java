package io.github.kansamu216.vigil.protocol.payload;

import io.github.kansamu216.vigil.model.AuthRequest;
import io.github.kansamu216.vigil.protocol.ProtocolException;
import io.github.kansamu216.vigil.protocol.ProtocolLimits;
import io.github.kansamu216.vigil.protocol.WireReader;
import io.github.kansamu216.vigil.protocol.WireWriter;

/**
 * 认证应答报文编解码。
 */
public final class AuthPayload {

    private AuthPayload() {
    }

    public static byte[] encode(AuthRequest request) {
        WireWriter writer = new WireWriter(512, ProtocolLimits.MAX_PAYLOAD_BYTES);
        writer.string(request.sessionId());
        writer.string(request.nonce());
        writer.bytes(request.wrappedSessionKey());
        writer.bytes(request.encryptedReport());
        return writer.toByteArray();
    }

    public static AuthRequest decode(byte[] payload) throws ProtocolException {
        WireReader reader = new WireReader(payload);
        String sessionId = reader.string();
        String nonce = reader.string();
        byte[] wrappedKey = reader.bytes();
        byte[] encryptedReport = reader.bytes();
        if (reader.remaining() != 0) {
            throw new ProtocolException(
                    ProtocolException.Code.LENGTH_MISMATCH, "trailing bytes in auth payload");
        }
        try {
            return new AuthRequest(sessionId, nonce, wrappedKey, encryptedReport);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException(ProtocolException.Code.FIELD_OUT_OF_RANGE, e.getMessage());
        }
    }
}
