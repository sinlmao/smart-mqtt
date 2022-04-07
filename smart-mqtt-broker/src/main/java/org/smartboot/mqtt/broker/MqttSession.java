package org.smartboot.mqtt.broker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartboot.mqtt.common.StoredMessage;
import org.smartboot.mqtt.common.enums.MqttQoS;
import org.smartboot.mqtt.common.message.MqttMessage;
import org.smartboot.mqtt.common.message.MqttPublishMessage;
import org.smartboot.socket.transport.AioSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 会话，客户端和服务端之间的状态交互。
 * 一些会话持续时长与网络连接一样，另一些可以在客户端和服务端的多个连续网络连接间扩展。
 *
 * @author 三刀
 * @version V1.0 , 2018/4/26
 */
public class MqttSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(MqttSession.class);
    /**
     * 用于生成当前会话的报文标识符
     */
    private final AtomicInteger packetIdCreator = new AtomicInteger(1);
    /**
     * 当前连接订阅的Topic的消费信息
     */
    private final Map<String, TopicSubscriber> consumeOffsets = new ConcurrentHashMap<>();

    private final ConcurrentMap<Integer, StoredMessage> inboundInflightMessages = new ConcurrentHashMap<>();

    private final AioSession session;
    private final BrokerContext mqttContext;
    private String clientId;
    private String username;
    /**
     * 最近一次收到客户端消息的时间
     */
    private long latestReceiveMessageSecondTime;

    private boolean closed = false;

    /**
     * 已授权
     */
    private boolean authorized;

    /**
     * 遗嘱消息
     */
    private StoredMessage willMessage;

    public MqttSession(BrokerContext mqttContext, AioSession session) {
        this.mqttContext = mqttContext;
        this.session = session;
    }

    public void publish(MqttPublishMessage publishMessage) {
        LOGGER.info("publish to client:{}, topic:{} packetId:{}", clientId, publishMessage.getMqttPublishVariableHeader().topicName(), publishMessage.getMqttPublishVariableHeader().packetId());
        //QoS1 响应监听
        if (publishMessage.getMqttFixedHeader().getQosLevel() == MqttQoS.AT_LEAST_ONCE) {
            putInFightMessage(publishMessage.getMqttPublishVariableHeader().packetId(), BrokerContextImpl.asStoredMessage(publishMessage));
        }
        write(publishMessage);
    }

    public synchronized void write(MqttMessage mqttMessage) {
        try {
            mqttMessage.writeTo(session.writeBuffer());
            session.writeBuffer().flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        if (closed) {
            return;
        }

        if (willMessage != null) {
            //非正常中断，推送遗嘱消息
            mqttContext.publish(mqttContext.getOrCreateTopic(willMessage.getTopic()), willMessage);
        }
        consumeOffsets.keySet().forEach(this::unsubscribe);
        mqttContext.removeSession(this);
        session.close(false);
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    public long getLatestReceiveMessageSecondTime() {
        return latestReceiveMessageSecondTime;
    }

    public void setLatestReceiveMessageSecondTime(long latestReceiveMessageSecondTime) {
        this.latestReceiveMessageSecondTime = latestReceiveMessageSecondTime;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void putInFightMessage(int packetId, StoredMessage storedMessage) {
        inboundInflightMessages.put(packetId, storedMessage);
    }

    public StoredMessage pollInFightMessage(int packetId) {
        return inboundInflightMessages.remove(packetId);
    }

    /*
     * 如果服务端收到一个 SUBSCRIBE 报文，
     * 报文的主题过滤器与一个现存订阅的主题过滤器相同，
     * 那么必须使用新的订阅彻底替换现存的订阅。
     * 新订阅的主题过滤器和之前订阅的相同，但是它的最大 QoS 值可以不同。
     */
    public synchronized void subscribeTopic(TopicSubscriber subscription) {

        unsubscribe(subscription.getTopic().getTopic());
        consumeOffsets.put(subscription.getTopic().getTopic(), subscription);
        subscription.getTopic().getConsumeOffsets().put(this, subscription);
        LOGGER.info("subscribe topic:{} success, clientId:{}", subscription.getTopic(), clientId);
    }

    public void unsubscribe(String topic) {
        TopicSubscriber oldOffset = consumeOffsets.remove(topic);
        if (oldOffset != null) {
            oldOffset.setEnable(false);
            oldOffset.getTopic().getConsumeOffsets().remove(oldOffset.getMqttSession());
            LOGGER.info("unsubscribe topic:{} success,oldClientId:{} ,currentClientId:{}", topic, oldOffset.getMqttSession().clientId, clientId);
        }
    }

    public int newPacketId() {
        return packetIdCreator.getAndIncrement();
    }

    public boolean isAuthorized() {
        return authorized;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    public StoredMessage getWillMessage() {
        return willMessage;
    }

    public void setWillMessage(StoredMessage willMessage) {
        this.willMessage = willMessage;
    }
}
