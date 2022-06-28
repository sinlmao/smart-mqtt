package org.smartboot.mqtt.broker.eventbus;

import org.smartboot.mqtt.common.message.MqttPublishMessage;

/**
 * 消息存储服务
 * <p>
 * 消息总线Provider
 *
 * @author 三刀（zhengjunweimail@163.com）
 * @version V1.0 , 2022/4/4
 */
public interface MessageBus {

    void subscribe(Subscriber subscriber);

    void subscribe(Subscriber subscriber, EventFilter filter);

    /**
     * 发布消息至总线
     */
    EventMessage publish(MqttPublishMessage storedMessage);

}
