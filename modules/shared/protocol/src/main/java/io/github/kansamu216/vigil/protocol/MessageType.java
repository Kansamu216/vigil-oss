package io.github.kansamu216.vigil.protocol;

public enum MessageType {

    CHALLENGE(0x0001),

    AUTH(0x0002),

    KEEPALIVE(0x0003),

    PING(0x0004),

    VERDICT(0x0005),

    SCREEN_REQUEST(0x0101),

    SCREEN_CHUNK(0x0102);

    private final int id;

    MessageType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static MessageType fromId(int id) throws ProtocolException {
        for (MessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new ProtocolException(
                ProtocolException.Code.UNKNOWN_MESSAGE, "unknown message id: " + id);
    }
}
