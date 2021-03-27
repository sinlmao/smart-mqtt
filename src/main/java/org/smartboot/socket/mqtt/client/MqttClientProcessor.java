package org.smartboot.socket.mqtt.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartboot.socket.MessageProcessor;
import org.smartboot.socket.StateMachineEnum;
import org.smartboot.socket.mqtt.MqttContext;
import org.smartboot.socket.mqtt.MqttServerContext;
import org.smartboot.socket.mqtt.MqttSession;
import org.smartboot.socket.mqtt.message.*;
import org.smartboot.socket.mqtt.processor.MqttProcessor;
import org.smartboot.socket.mqtt.processor.client.*;
import org.smartboot.socket.mqtt.processor.server.SubscribeProcessor;
import org.smartboot.socket.transport.AioSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author 三刀
 * @version V1.0 , 2018/4/24
 */
public class MqttClientProcessor implements MessageProcessor<MqttMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MqttClientProcessor.class);
    private Map<Class<? extends MqttMessage>, MqttProcessor> processorMap = new HashMap<>();
    private MqttContext mqttContext = new MqttServerContext();
    private Map<String, MqttSession> sessionMap = new ConcurrentHashMap();

    {
        processorMap.put(MqttPingRespMessage.class, new PingRespProcessor());
        processorMap.put(MqttConnAckMessage.class, new ConnAckProcessor());
        processorMap.put(MqttPubAckMessage.class, new PubAckProcessor());
        processorMap.put(MqttPubRecMessage.class, new PubRecProcessor());
        processorMap.put(MqttPubCompMessage.class, new PubCompProcessor());
        processorMap.put(MqttSubAckMessage.class, new SubscribeProcessor());
    }


    @Override
    public void process(AioSession session, MqttMessage msg) {
        LOGGER.info("process msg:{}",msg);
        MqttProcessor processor = processorMap.get(msg.getClass());
        if (processor != null) {
            processor.process(mqttContext, sessionMap.get(session.getSessionID()), msg);
        } else {
            System.out.println(msg);
        }
    }

    @Override
    public void stateEvent(AioSession session, StateMachineEnum stateMachineEnum, Throwable throwable) {
        switch (stateMachineEnum) {
            case NEW_SESSION:
                sessionMap.put(session.getSessionID(), new MqttSession(session));
                break;
        }
        System.out.println(stateMachineEnum);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
}
