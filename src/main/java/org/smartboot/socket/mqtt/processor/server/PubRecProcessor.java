package org.smartboot.socket.mqtt.processor.server;

import org.smartboot.socket.mqtt.MqttContext;
import org.smartboot.socket.mqtt.MqttSession;
import org.smartboot.socket.mqtt.enums.MqttMessageType;
import org.smartboot.socket.mqtt.enums.MqttQoS;
import org.smartboot.socket.mqtt.message.MqttFixedHeader;
import org.smartboot.socket.mqtt.message.MqttPubRecMessage;
import org.smartboot.socket.mqtt.message.MqttPubRelMessage;
import org.smartboot.socket.mqtt.processor.MqttProcessor;

/**
 * PUBREC 报文是对 QoS 等级 2 的 PUBLISH 报文的响应。它是 QoS 2 等级协议交换的第二个报文。
 *
 * @author 三刀（zhengjunweimail@163.com）
 * @version V1.0 , 2022/3/27
 */
public class PubRecProcessor implements MqttProcessor<MqttPubRecMessage> {
    @Override
    public void process(MqttContext context, MqttSession session, MqttPubRecMessage mqttPubRelMessage) {
        //发送pubRel消息。
        MqttPubRelMessage pubRelMessage = new MqttPubRelMessage(new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_MOST_ONCE, false, 0));
        pubRelMessage.setPacketId(mqttPubRelMessage.getPacketId());
        session.write(pubRelMessage);
    }
}
