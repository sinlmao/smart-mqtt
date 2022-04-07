package org.smartboot.mqtt.client;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartboot.mqtt.common.MqttMessageBuilders;
import org.smartboot.mqtt.common.enums.MqttConnectReturnCode;
import org.smartboot.mqtt.common.enums.MqttMessageType;
import org.smartboot.mqtt.common.enums.MqttQoS;
import org.smartboot.mqtt.common.message.MqttConnAckMessage;
import org.smartboot.mqtt.common.message.MqttConnectMessage;
import org.smartboot.mqtt.common.message.MqttConnectPayload;
import org.smartboot.mqtt.common.message.MqttConnectVariableHeader;
import org.smartboot.mqtt.common.message.MqttFixedHeader;
import org.smartboot.mqtt.common.message.MqttMessage;
import org.smartboot.mqtt.common.message.MqttPacketIdentifierMessage;
import org.smartboot.mqtt.common.message.MqttPingReqMessage;
import org.smartboot.mqtt.common.message.MqttPingRespMessage;
import org.smartboot.mqtt.common.message.MqttPubAckMessage;
import org.smartboot.mqtt.common.message.MqttPubCompMessage;
import org.smartboot.mqtt.common.message.MqttPubRecMessage;
import org.smartboot.mqtt.common.message.MqttPubRelMessage;
import org.smartboot.mqtt.common.message.MqttPublishMessage;
import org.smartboot.mqtt.common.message.MqttSubAckMessage;
import org.smartboot.mqtt.common.message.MqttSubscribeMessage;
import org.smartboot.mqtt.common.message.MqttTopicSubscription;
import org.smartboot.mqtt.common.message.WillMessage;
import org.smartboot.mqtt.common.protocol.MqttProtocol;
import org.smartboot.mqtt.common.util.ValidateUtils;
import org.smartboot.socket.buffer.BufferPagePool;
import org.smartboot.socket.extension.plugins.HeartPlugin;
import org.smartboot.socket.extension.processor.AbstractMessageProcessor;
import org.smartboot.socket.transport.AioQuickClient;
import org.smartboot.socket.transport.AioSession;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MqttClient implements Closeable {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
    /**
     * 用于生成当前会话的报文标识符
     */
    private final AtomicInteger packetIdCreator = new AtomicInteger(1);
    private final String clientId;
    private final MqttClientConfigure clientConfigure = new MqttClientConfigure();
    private final AbstractMessageProcessor<MqttMessage> messageProcessor = new MqttClientProcessor(this);
    private final ConcurrentLinkedQueue<Runnable> waitTasks = new ConcurrentLinkedQueue<>();
    private final Map<String, Subscribe> subscribes = new ConcurrentHashMap<>();
    private AioQuickClient client;
    private AioSession aioSession;
    private AsynchronousChannelGroup asynchronousChannelGroup;
    private BufferPagePool bufferPagePool;
    private boolean connected = false;
    private Map<Integer, Consumer<? extends MqttPacketIdentifierMessage>> responseConsumers = new ConcurrentHashMap<>();

    public MqttClient(String host, int port, String clientId) {
        clientConfigure.setHost(host);
        clientConfigure.setPort(port);
        this.clientId = clientId;
    }

    public void connect() {
        try {
            asynchronousChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
                private AtomicInteger index = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "ClientGroup" + index.getAndIncrement());
                }
            });
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        connect(asynchronousChannelGroup, clientConfigure.getConnectAckConsumer());
    }

    public void connect(AsynchronousChannelGroup asynchronousChannelGroup, Consumer<MqttConnAckMessage> consumer) {
        if (bufferPagePool == null) {
            bufferPagePool = new BufferPagePool(1024 * 1024 * 2, 10, true);
        }
        //设置 connect ack 回调事件
        clientConfigure.setConnectAckConsumer(mqttConnAckMessage -> {
            gcConfigure();
            //连接成功,注册订阅消息
            if (mqttConnAckMessage.getMqttConnAckVariableHeader().connectReturnCode() == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
                connected = true;
                Runnable runnable;
                while ((runnable = waitTasks.poll()) != null) {
                    runnable.run();
                }
            }

            consumer.accept(mqttConnAckMessage);
        });
        //启动心跳插件
        if (clientConfigure.getKeepAliveInterval() > 0) {
            messageProcessor.addPlugin(new HeartPlugin<MqttMessage>(clientConfigure.getKeepAliveInterval() * 1000 - 200, TimeUnit.MILLISECONDS) {

                @Override
                public void sendHeartRequest(AioSession session) throws IOException {
                    if (connected) {
                        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.PINGREQ, false, MqttQoS.AT_MOST_ONCE, false, 0);
                        MqttPingReqMessage pingReqMessage = new MqttPingReqMessage(mqttFixedHeader);
                        write(pingReqMessage);
                    }
                }

                @Override
                public boolean isHeartMessage(AioSession session, MqttMessage msg) {
                    return connected && msg instanceof MqttPingRespMessage;
                }
            });
        }
//        messageProcessor.addPlugin(new StreamMonitorPlugin<>());
        client = new AioQuickClient(clientConfigure.getHost(), clientConfigure.getPort(), new MqttProtocol(), messageProcessor);
        try {
            client.setBufferPagePool(bufferPagePool);
            client.setWriteBuffer(1024 * 1024, 10);
            aioSession = client.start(asynchronousChannelGroup);
            //remainingLength 字段动态计算，此处可传入任意值
            MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);
            MqttConnectVariableHeader variableHeader = new MqttConnectVariableHeader(clientConfigure.getMqttVersion(), StringUtils.isNotBlank(clientConfigure.getUserName()), clientConfigure.getPassword() != null, clientConfigure.getWillMessage(), clientConfigure.isCleanSession(), clientConfigure.getKeepAliveInterval());
            MqttConnectPayload payload = new MqttConnectPayload(clientId, clientConfigure.getWillMessage().getWillTopic(), clientConfigure.getWillMessage().getWillMessage(), clientConfigure.getUserName(), clientConfigure.getPassword());
            MqttConnectMessage connectMessage = new MqttConnectMessage(mqttFixedHeader, variableHeader, payload);
            write(connectMessage);
            connected = true;
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * 释放本地内存
     */
    private void gcConfigure() {
        clientConfigure.setWillMessage(null);
    }

    public synchronized void write(MqttMessage mqttMessage) {
        try {
            mqttMessage.writeTo(aioSession.writeBuffer());
            aioSession.writeBuffer().flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void write(MqttPacketIdentifierMessage mqttMessage, Consumer<? extends MqttPacketIdentifierMessage> consumer) {
        try {
            responseConsumers.put(mqttMessage.getPacketId(), consumer);
            mqttMessage.writeTo(aioSession.writeBuffer());
            aioSession.writeBuffer().flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MqttClient subscribe(String topic, MqttQoS qos, BiConsumer<MqttClient, MqttPublishMessage> consumer) {
        return subscribe(new String[]{topic}, new MqttQoS[]{qos}, consumer);
    }

    public MqttClient subscribe(String[] topics, MqttQoS[] qos, BiConsumer<MqttClient, MqttPublishMessage> consumer) {
        if (connected) {
            subscribe0(topics, qos, consumer);
        } else {
            waitTasks.offer(() -> subscribe0(topics, qos, consumer));
        }
        return this;
    }

    public void subscribe0(String[] topic, MqttQoS[] qos, BiConsumer<MqttClient, MqttPublishMessage> consumer) {
        MqttMessageBuilders.SubscribeBuilder subscribeBuilder = MqttMessageBuilders.subscribe().packetId(newPacketId());
        for (int i = 0; i < topic.length; i++) {
            subscribeBuilder.addSubscription(qos[i], topic[i]);
        }
        MqttSubscribeMessage subscribeMessage = subscribeBuilder.build();
        responseConsumers.put(subscribeMessage.getPacketId(), (Consumer<MqttSubAckMessage>) mqttMessage -> {
            List<MqttTopicSubscription> subscriptions = subscribeMessage.getMqttSubscribePayload().topicSubscriptions();
            int i = 0;
            for (MqttTopicSubscription subscription : subscriptions) {
                MqttQoS minQos = MqttQoS.valueOf(Math.min(subscription.qualityOfService().value(), qos[i++].value()));
                clientConfigure.getTopicListener().subscribe(subscription.topicFilter(), subscription.qualityOfService() == MqttQoS.FAILURE ? MqttQoS.FAILURE : minQos);
                if (subscription.qualityOfService() != MqttQoS.FAILURE) {
                    subscribes.put(subscription.topicFilter(), new Subscribe(subscription.topicFilter(), minQos, consumer));
                }
            }
        });
        write(subscribeMessage);
    }

    public void notifyResponse(MqttPacketIdentifierMessage message) {
        Consumer consumer = responseConsumers.get(message.getPacketId());
        consumer.accept(message);
    }


    /**
     * 设置遗嘱消息，必须在connect之前调用
     */
    public MqttClient willMessage(WillMessage willMessage) {
        clientConfigure.setWillMessage(willMessage);
        return this;
    }

    public void publish(String topic, MqttQoS qos, byte[] payload, boolean retain, Consumer<Integer> consumer) {
        if (connected) {
            publish0(topic, qos, payload, retain, consumer);
        } else {
            waitTasks.offer(() -> publish0(topic, qos, payload, retain, consumer));
        }
    }

    private void publish0(String topic, MqttQoS qos, byte[] payload, boolean retain, Consumer<Integer> consumer) {
        ValidateUtils.notNull(qos, "qos is null");
        MqttMessageBuilders.PublishBuilder publishBuilder = MqttMessageBuilders.publish().topicName(topic).qos(qos).payload(payload).retained(retain);
        if (qos.value() > 0) {
            int packetId = newPacketId();
            publishBuilder.packetId(packetId);
            if (qos == MqttQoS.AT_LEAST_ONCE) {
                //至少一次
                responseConsumers.put(packetId, message -> {
                    ValidateUtils.isTrue(message instanceof MqttPubAckMessage, "invalid message type");
                    responseConsumers.remove(packetId);
                    LOGGER.info("Qos1消息发送成功...");
                    consumer.accept(packetId);
                });
            } else if (qos == MqttQoS.EXACTLY_ONCE) {
                //只有一次
                responseConsumers.put(packetId, message -> {
                    ValidateUtils.isTrue(message instanceof MqttPubRecMessage, "invalid message type");
                    responseConsumers.put(packetId, (Consumer<MqttPubCompMessage>) compMessage -> {
                        LOGGER.info("Qos2消息发送成功...");
                        consumer.accept(compMessage.getPacketId());
                    });
                    MqttPubRelMessage pubRelMessage = new MqttPubRelMessage(new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_MOST_ONCE, false, 0));
                    pubRelMessage.setPacketId(message.getPacketId());
                    write(pubRelMessage);
                });
            }

        }
        write(publishBuilder.build());
    }

    public int newPacketId() {
        return packetIdCreator.getAndIncrement();
    }

    public MqttClientConfigure getClientConfigure() {
        return clientConfigure;
    }

    public Map<String, Subscribe> getSubscribes() {
        return subscribes;
    }

    @Override
    public void close() throws IOException {
        client.shutdown();
    }

}
