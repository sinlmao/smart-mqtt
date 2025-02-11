package org.smartboot.mqtt.common.message;

import org.smartboot.mqtt.common.MqttWriter;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author 三刀
 * @version V1.0 , 2018/4/24
 */
class OnlyFixedHeaderMessage extends MqttMessage {
    private static final MqttVariableHeader NONE_VARIABLE_HEADER = new MqttVariableHeader(null) {
        @Override
        public int preEncode0() {
            return 0;
        }

        @Override
        public void writeTo(MqttWriter mqttWriter) throws IOException {

        }
    };

    public OnlyFixedHeaderMessage(MqttFixedHeader mqttFixedHeader) {
        super(mqttFixedHeader);
    }

    @Override
    public final void decodeVariableHeader(ByteBuffer buffer) {
    }

    @Override
    public MqttVariableHeader getVariableHeader() {
        return NONE_VARIABLE_HEADER;
    }
}
