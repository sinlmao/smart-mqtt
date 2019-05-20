/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.smartboot.socket.mqtt.message;


import org.smartboot.socket.mqtt.enums.MqttQoS;
import org.smartboot.socket.mqtt.enums.MqttVersion;

public final class MqttCodecUtil {

    private static final char[] TOPIC_WILDCARDS = {'#', '+'};
    private static final int MIN_CLIENT_ID_LENGTH = 1;
    private static final int MAX_CLIENT_ID_LENGTH = 23;

    private MqttCodecUtil() {
    }

    static boolean isValidPublishTopicName(String topicName) {
        // publish topic name must not contain any wildcard
        for (char c : TOPIC_WILDCARDS) {
            if (topicName.indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    static boolean isValidMessageId(int messageId) {
        return messageId != 0;
    }

    static boolean isValidClientId(MqttVersion mqttVersion, String clientId) {
        if (mqttVersion == MqttVersion.MQTT_3_1) {
            return clientId != null && clientId.length() >= MIN_CLIENT_ID_LENGTH &&
                    clientId.length() <= MAX_CLIENT_ID_LENGTH;
        }
        if (mqttVersion == MqttVersion.MQTT_3_1_1) {
            // In 3.1.3.1 Client Identifier of MQTT 3.1.1 specification, The Server MAY allow ClientId’s
            // that contain more than 23 encoded bytes. And, The Server MAY allow zero-length ClientId.
            return clientId != null;
        }
        throw new IllegalArgumentException(mqttVersion + " is unknown mqtt version");
    }

    public static MqttFixedHeader resetUnusedFields(MqttFixedHeader mqttFixedHeader) {
        switch (mqttFixedHeader.getMessageType()) {
            case CONNECT:
            case CONNACK:
            case PUBACK:
            case PUBREC:
            case PUBCOMP:
            case SUBACK:
            case UNSUBACK:
            case PINGREQ:
            case PINGRESP:
            case DISCONNECT:
                if (mqttFixedHeader.isDup() ||
                        mqttFixedHeader.getQosLevel() != MqttQoS.AT_MOST_ONCE ||
                        mqttFixedHeader.isRetain()) {
                    return new MqttFixedHeader(
                            mqttFixedHeader.getMessageType(),
                            false,
                            MqttQoS.AT_MOST_ONCE,
                            false,
                            mqttFixedHeader.remainingLength());
                }
                return mqttFixedHeader;
            case PUBREL:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
                if (mqttFixedHeader.isRetain()) {
                    return new MqttFixedHeader(
                            mqttFixedHeader.getMessageType(),
                            mqttFixedHeader.isDup(),
                            mqttFixedHeader.getQosLevel(),
                            false,
                            mqttFixedHeader.remainingLength());
                }
                return mqttFixedHeader;
            default:
                return mqttFixedHeader;
        }
    }
}
