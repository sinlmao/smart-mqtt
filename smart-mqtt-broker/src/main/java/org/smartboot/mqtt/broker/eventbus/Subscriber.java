package org.smartboot.mqtt.broker.eventbus;

/**
 * @author 三刀（zhengjunweimail@163.com）
 * @version V1.0 , 2022/6/25
 */
public interface Subscriber {
    void subscribe(EventMessage message);
}
