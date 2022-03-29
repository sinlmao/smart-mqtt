package org.smartboot.socket.mqtt.processor.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartboot.socket.mqtt.MqttContext;
import org.smartboot.socket.mqtt.MqttSession;
import org.smartboot.socket.mqtt.message.MqttPubCompMessage;
import org.smartboot.socket.mqtt.processor.MqttProcessor;

/**
 * PUBREC 报文是对 QoS 等级 2 的 PUBLISH 报文的响应。它是 QoS 2 等级协议交换的第二个报文。
 *
 * @author 三刀（zhengjunweimail@163.com）
 * @version V1.0 , 2022/3/27
 */
public class PubCompProcessor implements MqttProcessor<MqttPubCompMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PubCompProcessor.class);

    @Override
    public void process(MqttContext context, MqttSession session, MqttPubCompMessage mqttPubCompMessage) {
        LOGGER.info("pubComp message:{}", mqttPubCompMessage);
    }
}
