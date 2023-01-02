package org.smartboot.mqtt.common.message;

/**
 * @author 三刀
 * @version V1.0 , 2018/4/22
 */
public class MqttPublishVariableHeader extends MqttPacketIdVariableHeader {
    /**
     * PUBLISH 报文中的主题名不能包含通配符
     */
    private String topicName;

    private MqttProperties properties;

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public MqttProperties getProperties() {
        return properties;
    }

    public void setProperties(MqttProperties properties) {
        this.properties = properties;
    }
}
