package org.smartboot.socket.mqtt.processor;

import org.smartboot.socket.mqtt.MqttContext;
import org.smartboot.socket.mqtt.MqttSession;
import org.smartboot.socket.mqtt.message.MqttMessage;

/**
 * @author 三刀
 * @version V1.0 , 2018/4/25
 */
public interface MqttProcessor<T extends MqttMessage> {

    /**
     * 处理Mqtt消息
     * @param context
     * @param session
     * @param t
     */
    void process(MqttContext context, MqttSession session, T t);
}
