package io.github.kansamu216.vigil.protocol;

/**
 * 报文方向。AAD 必须绑定方向，否则同一密文可被反射回发送方。
 */
public enum Direction {

    CLIENT_TO_SERVER(0),
    SERVER_TO_CLIENT(1);

    private final int wireValue;

    Direction(int wireValue) {
        this.wireValue = wireValue;
    }

    public int wireValue() {
        return wireValue;
    }

    public static Direction fromWireValue(int value) throws ProtocolException {
        for (Direction direction : values()) {
            if (direction.wireValue == value) {
                return direction;
            }
        }
        throw new ProtocolException(
                ProtocolException.Code.INVALID_DIRECTION, "invalid direction: " + value);
    }
}
