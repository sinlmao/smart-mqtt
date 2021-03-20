package org.smartboot.socket.mqtt;

import org.smartboot.socket.mqtt.message.MqttConnAckMessage;
import org.smartboot.socket.mqtt.message.MqttConnectMessage;
import org.smartboot.socket.mqtt.message.MqttDisconnectMessage;
import org.smartboot.socket.mqtt.message.MqttFixedHeader;
import org.smartboot.socket.mqtt.message.MqttInvalidMessage;
import org.smartboot.socket.mqtt.message.MqttMessage;
import org.smartboot.socket.mqtt.message.MqttPingReqMessage;
import org.smartboot.socket.mqtt.message.MqttPingRespMessage;
import org.smartboot.socket.mqtt.message.MqttPubAckMessage;
import org.smartboot.socket.mqtt.message.MqttPubCompMessage;
import org.smartboot.socket.mqtt.message.MqttPublishMessage;
import org.smartboot.socket.mqtt.message.MqttPubRecMessage;
import org.smartboot.socket.mqtt.message.MqttPubRelMessage;
import org.smartboot.socket.mqtt.message.MqttSubAckMessage;
import org.smartboot.socket.mqtt.message.MqttSubscribeMessage;
import org.smartboot.socket.mqtt.message.MqttUnsubAckMessage;
import org.smartboot.socket.mqtt.message.MqttUnsubscribeMessage;

public final class MqttMessageFactory {

    private MqttMessageFactory() {
    }

    public static MqttMessage newMessage(MqttFixedHeader mqttFixedHeader) {
        switch (mqttFixedHeader.getMessageType()) {
            case CONNECT:
                return new MqttConnectMessage(mqttFixedHeader);

            case CONNACK:
                return new MqttConnAckMessage(mqttFixedHeader);

            case SUBSCRIBE:
                return new MqttSubscribeMessage(mqttFixedHeader);

            case SUBACK:
                return new MqttSubAckMessage(mqttFixedHeader);

            case UNSUBACK:
                return new MqttUnsubAckMessage(mqttFixedHeader);

            case UNSUBSCRIBE:
                return new MqttUnsubscribeMessage(mqttFixedHeader);

            case PUBLISH:
                return new MqttPublishMessage(mqttFixedHeader);

            case PUBACK:
                return new MqttPubAckMessage(mqttFixedHeader);
            case PUBREC:
                return new MqttPubRecMessage(mqttFixedHeader);
            case PUBREL:
                return new MqttPubRelMessage(mqttFixedHeader);
            case PUBCOMP:
                return new MqttPubCompMessage(mqttFixedHeader);

            case PINGREQ:
                return new MqttPingReqMessage(mqttFixedHeader);
            case PINGRESP:
                return new MqttPingRespMessage(mqttFixedHeader);
            case DISCONNECT:
                return new MqttDisconnectMessage(mqttFixedHeader);

            default:
                throw new IllegalArgumentException("unknown message type: " + mqttFixedHeader.getMessageType());
        }
    }

    public static MqttMessage newInvalidMessage(Throwable cause) {
        cause.printStackTrace();
        return new MqttInvalidMessage(null);
    }
}
