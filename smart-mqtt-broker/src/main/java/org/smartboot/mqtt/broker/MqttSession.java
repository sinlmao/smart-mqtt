package org.smartboot.mqtt.broker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartboot.mqtt.broker.eventbus.ServerEventType;
import org.smartboot.mqtt.broker.persistence.message.PersistenceMessage;
import org.smartboot.mqtt.broker.persistence.message.PersistenceProvider;
import org.smartboot.mqtt.broker.persistence.session.SessionState;
import org.smartboot.mqtt.broker.plugin.provider.TopicTokenUtil;
import org.smartboot.mqtt.common.AbstractSession;
import org.smartboot.mqtt.common.AsyncTask;
import org.smartboot.mqtt.common.InflightQueue;
import org.smartboot.mqtt.common.QosPublisher;
import org.smartboot.mqtt.common.TopicToken;
import org.smartboot.mqtt.common.enums.MqttQoS;
import org.smartboot.mqtt.common.eventbus.EventBusSubscriber;
import org.smartboot.mqtt.common.eventbus.EventType;
import org.smartboot.mqtt.common.message.MqttPublishMessage;
import org.smartboot.mqtt.common.util.MqttUtil;
import org.smartboot.mqtt.common.util.ValidateUtils;
import org.smartboot.socket.transport.AioSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 会话，客户端和服务端之间的状态交互。
 * 一些会话持续时长与网络连接一样，另一些可以在客户端和服务端的多个连续网络连接间扩展。
 *
 * @author 三刀
 * @version V1.0 , 2018/4/26
 */
public class MqttSession extends AbstractSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(MqttSession.class);

    /**
     * 当前连接订阅的Topic的消费信息
     */
    private final Map<String, TopicFilterSubscriber> subscribers = new ConcurrentHashMap<>();

    private final BrokerContext mqttContext;
    private final InflightQueue inflightQueue;
    private String username;
    /**
     * 已授权
     */
    private boolean authorized;
    /**
     * 遗嘱消息
     */
    private MqttPublishMessage willMessage;
    private boolean cleanSession;

    public MqttSession(BrokerContext mqttContext, AioSession session, QosPublisher qosPublisher) {
        super(qosPublisher, mqttContext.getEventBus());
        this.mqttContext = mqttContext;
        this.session = session;
        this.inflightQueue = new InflightQueue(mqttContext.getBrokerConfigure().getMaxInflight());
        mqttContext.getEventBus().publish(ServerEventType.SESSION_CREATE, this);
    }


    public InflightQueue getInflightQueue() {
        return inflightQueue;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public synchronized void disconnect() {
        if (isDisconnect()) {
            return;
        }
        if (isAuthorized()) {
            if (cleanSession) {
                mqttContext.getProviders().getSessionStateProvider().remove(clientId);
            } else {
                //当清理会话标志为 0 的会话连接断开之后，服务端必须将之后的 QoS 1 和 QoS 2 级别的消息保存为会话状态的一部分，
                // 如果这些消息匹配断开连接时客户端的任何订阅
                SessionState sessionState = new SessionState();
                sessionState.getResponseConsumers().putAll(responseConsumers);
                //todo
//                subscribers.values().forEach(topicSubscriber -> sessionState.getSubscribers().add(new TopicSubscriber(topicSubscriber.getTopic(), null, topicSubscriber.getMqttQoS(), topicSubscriber.getNextConsumerOffset(), topicSubscriber.getRetainConsumerOffset())));
//                mqttContext.getProviders().getSessionStateProvider().store(clientId, sessionState);
            }
        }

        if (willMessage != null) {
            //非正常中断，推送遗嘱消息
            mqttContext.getMessageBus().publish(willMessage);
//            mqttContext.publish( willMessage.getVariableHeader().getTopicName());
        }
        subscribers.keySet().forEach(this::unsubscribe);
        MqttSession removeSession = mqttContext.removeSession(this.getClientId());
        if (removeSession != null && removeSession != this) {
            LOGGER.error("remove old session success:{}", removeSession);
            removeSession.disconnect();
        }
        LOGGER.info("remove mqttSession success:{}", removeSession);
        disconnect = true;
        try {
            session.close(false);
        } finally {
            mqttContext.getEventBus().publish(EventType.DISCONNECT, this);
        }
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

    public synchronized void subscribe(String topicFilter, MqttQoS mqttQoS) {
        subscribe0(topicFilter, mqttQoS,true);
    }

    private void subscribe0(String topicFilter, MqttQoS mqttQoS, boolean monitor) {
        TopicToken topicToken = new TopicToken(topicFilter);
        //精准匹配
        if (!topicToken.isWildcards()) {
            BrokerTopic topic = mqttContext.getOrCreateTopic(topicToken.getTopicFilter());//可能会先触发TopicFilterSubscriber.subscribe
            subscribe0(mqttQoS, topicToken, topic);
            return;
        }

        //通配符匹配存量Topic
        for (BrokerTopic topic : mqttContext.getTopics()) {
            if (TopicTokenUtil.match(topic.getTopicToken(), topicToken)) {
                subscribe0(mqttQoS, topicToken, topic);
            }
        }

        //通配符匹配增量Topic
        if(monitor) {
            mqttContext.getEventBus().subscribe(ServerEventType.TOPIC_CREATE, new EventBusSubscriber<>() {
                @Override
                public boolean enable() {
                    return !disconnect && subscribers.containsKey(topicFilter);
                }

                @Override
                public void subscribe(EventType<BrokerTopic> eventType, BrokerTopic object) {
                    if (TopicTokenUtil.match(object.getTopicToken(), topicToken)) {
                        MqttSession.this.subscribe0(mqttQoS, topicToken, object);
                    }
                }
            });
        }
    }

    private void subscribe0(MqttQoS mqttQoS, TopicToken topicToken, BrokerTopic topic) {
        LOGGER.info("topicFilter:{} subscribe topic:{} success!", topicToken.getTopicFilter(), topic.getTopic());
        long latestOffset = mqttContext.getProviders().getPersistenceProvider().getLatestOffset(topic.getTopic());
        long retainOldestOffset = mqttContext.getProviders().getRetainMessageProvider().getOldestOffset(topic.getTopic());
        //以当前消息队列的最新点位为起始点位
        TopicSubscriber subscription = new TopicSubscriber(topic, this, mqttQoS, latestOffset + 1, retainOldestOffset);
        subscription.setTopicFilterToken(topicToken);
        // 如果服务端收到一个 SUBSCRIBE 报文，
        //报文的主题过滤器与一个现存订阅的主题过滤器相同，
        // 那么必须使用新的订阅彻底替换现存的订阅。
        // 新订阅的主题过滤器和之前订阅的相同，但是它的最大 QoS 值可以不同。
        ValidateUtils.isTrue(!disconnect, "session has closed,can not subscribe topic");
        unsubscribe0(subscription.getTopic().getTopic());
        TopicFilterSubscriber topicFilterSubscriber = subscribers.get(subscription.getTopicFilterToken().getTopicFilter());
        if (topicFilterSubscriber == null) {
            topicFilterSubscriber = new TopicFilterSubscriber(subscription.getTopicFilterToken(), subscription.getMqttQoS(), subscription);
            subscribers.put(subscription.getTopicFilterToken().getTopicFilter(), topicFilterSubscriber);
        } else {
            topicFilterSubscriber.getTopicSubscribers().put(subscription.getTopic().getTopic(), subscription);
        }
        TopicSubscriber preTopicSubscriber = subscription.getTopic().getConsumeOffsets().put(this, subscription);
        if (preTopicSubscriber != null) {
            LOGGER.error("invalid state...");
        }

        mqttContext.getEventBus().publish(ServerEventType.SUBSCRIBE_TOPIC, subscription);
    }

    private void unsubscribe0(String topic) {
        subscribers.values().forEach(topicFilterSubscriber -> {
            TopicSubscriber oldOffset = topicFilterSubscriber.getTopicSubscribers().remove(topic);
            if (oldOffset != null) {
                TopicSubscriber consumerOffset = oldOffset.getTopic().getConsumeOffsets().remove(this);
                LOGGER.info("unsubscribe topic:{} {},", topic, oldOffset == consumerOffset ? "success" : "fail");
            }
        });
    }

    public void resubscribe() {
        subscribers.values().forEach(subscriber -> subscribe0(subscriber.getTopicFilterToken().getTopicFilter(), subscriber.getMqttQoS(),false));
    }

    public void unsubscribe(String topicFilter) {
        subscribers.remove(topicFilter).getTopicSubscribers().keySet().forEach(this::unsubscribe0);
    }


    public boolean isAuthorized() {
        return authorized;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    public void setWillMessage(MqttPublishMessage willMessage) {
        this.willMessage = willMessage;
    }

    public void batchPublish(TopicSubscriber consumeOffset, ExecutorService executorService) {
        if (consumeOffset.getSemaphore().availablePermits() > 1) {
            LOGGER.error("invalid semaphore:{}", consumeOffset.getSemaphore().availablePermits());
        }
        if (consumeOffset.getSemaphore().tryAcquire()) {
            publish0(consumeOffset, executorService);
        }
    }

    private void publish0(TopicSubscriber consumeOffset, ExecutorService executorService) {
        long nextConsumerOffset = consumeOffset.getNextConsumerOffset();
        PersistenceProvider persistenceProvider = mqttContext.getProviders().getPersistenceProvider();
        int count = 0;

        while (!consumeOffset.getMqttSession().getInflightQueue().isFull()) {
            PersistenceMessage eventMessage = persistenceProvider.get(consumeOffset.getTopic().getTopic(), nextConsumerOffset + count);
            if (eventMessage == null) {
                break;
            }
            count++;
            MqttSession mqttSession = consumeOffset.getMqttSession();
            MqttPublishMessage publishMessage = MqttUtil.createPublishMessage(mqttSession.newPacketId(), eventMessage.getTopic(), consumeOffset.getMqttQoS(), eventMessage.getPayload());
            InflightQueue inflightQueue = mqttSession.getInflightQueue();
            int index = inflightQueue.add(publishMessage, eventMessage.getOffset());
            mqttSession.publish(publishMessage, packetId -> {
                //最早发送的消息若收到响应，则更新点位
                boolean done = inflightQueue.commit(index, offset -> consumeOffset.setNextConsumerOffset(offset + 1));
                if (done) {
                    consumeOffset.setRetainConsumerTimestamp(eventMessage.getCreateTime());
                    inflightQueue.clear();
                    //本批次全部处理完毕
                    PersistenceMessage nextMessage = persistenceProvider.get(consumeOffset.getTopic().getTopic(), consumeOffset.getNextConsumerOffset());
                    if (nextMessage == null) {
                        consumeOffset.getSemaphore().release();
                    } else {
                        executorService.execute(new AsyncTask() {
                            @Override
                            public void execute() {
                                publish0(consumeOffset, executorService);
                            }
                        });

                    }
                }
            });
            mqttContext.getEventBus().publish(EventType.PUSH_PUBLISH_MESSAGE, this);
        }
        //无可publish的消息
        if (count == 0) {
            consumeOffset.getSemaphore().release();
        }
        //可能此时正好有新消息投递进来
        if (!consumeOffset.getMqttSession().getInflightQueue().isFull() && persistenceProvider.get(consumeOffset.getTopic().getTopic(), nextConsumerOffset) != null) {
            batchPublish(consumeOffset, executorService);
        }
    }
}
