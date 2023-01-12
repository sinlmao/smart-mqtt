package org.smartboot.mqtt.common.message;

import org.smartboot.mqtt.common.MqttWriter;
import org.smartboot.mqtt.common.ToString;
import org.smartboot.mqtt.common.enums.MqttVersion;
import org.smartboot.socket.util.DecoderException;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.smartboot.mqtt.common.message.MqttCodecUtil.decodeMsbLsb;

/**
 * @author 三刀
 * @version V1.0 , 2018/4/22
 */
public abstract class MqttMessage extends ToString {
    protected static final int PACKET_LENGTH = 2;
    /**
     * 固定报头
     */
    protected final MqttFixedHeader fixedHeader;
    protected MqttVersion version;

    public MqttMessage(MqttFixedHeader mqttFixedHeader) {
        this.fixedHeader = mqttFixedHeader;
    }

    public final MqttFixedHeader getFixedHeader() {
        return fixedHeader;
    }

    /**
     * 解码可变头部
     *
     * @param buffer
     */
    public abstract void decodeVariableHeader(ByteBuffer buffer);

    public void decodePlayLoad(ByteBuffer buffer) {

    }

    public void writeWithoutFixedHeader(MqttWriter mqttWriter) throws IOException {
        throw new UnsupportedOperationException();
    }


    protected final int decodeMessageId(ByteBuffer buffer) {
        final int messageId = decodeMsbLsb(buffer);
        if (messageId == 0) {
            throw new DecoderException("invalid messageId: " + messageId);
        }
        return messageId;
    }




    public MqttVersion getVersion() {
        return version;
    }

    public void setVersion(MqttVersion version) {
        this.version = version;
    }
}
