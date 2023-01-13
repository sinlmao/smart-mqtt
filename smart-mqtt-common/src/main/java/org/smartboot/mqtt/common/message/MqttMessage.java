package org.smartboot.mqtt.common.message;

import org.smartboot.mqtt.common.MqttWriter;
import org.smartboot.mqtt.common.ToString;
import org.smartboot.mqtt.common.enums.MqttVersion;
import org.smartboot.mqtt.common.message.payload.MqttPayload;
import org.smartboot.mqtt.common.message.variable.MqttVariableHeader;
import org.smartboot.socket.util.DecoderException;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.smartboot.mqtt.common.message.MqttCodecUtil.decodeMsbLsb;

/**
 * @author 三刀
 * @version V1.0 , 2018/4/22
 */
public abstract class MqttMessage extends ToString {
    private static final MqttPayload NONE_PAYLOAD = new MqttPayload();
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

    public void write(MqttWriter mqttWriter) throws IOException {
        MqttCodecUtil.writeFixedHeader(mqttWriter, getFixedHeader());
        MqttVariableHeader variableHeader = getVariableHeader();
        MqttPayload mqttPayload = getPayload();
        //剩余长度等于可变报头的长度（10 字节）加上有效载荷的长度。
        int remainingLength = variableHeader.preEncode() + mqttPayload.preEncode();

        //第一部分：固定报头
        MqttCodecUtil.writeVariableLengthInt(mqttWriter, remainingLength);

        //第二部分：可变报头
        variableHeader.writeTo(mqttWriter);

        //第三部分：有效载荷
        mqttPayload.writeTo(mqttWriter);
    }

    public abstract MqttVariableHeader getVariableHeader();

    public MqttPayload getPayload() {
        return NONE_PAYLOAD;
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
