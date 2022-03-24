package org.smartboot.socket.mqtt.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartboot.socket.mqtt.ToString;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

/**
 * @author 三刀
 * @version V1.0 , 2018/5/3
 */
public class Topic extends ToString {
    private static final Logger LOGGER = LoggerFactory.getLogger(Topic.class);
    private final String topic;
    /**
     * 当前Topic的所有订阅者
     */
    private final Set<String> subscribes = new ConcurrentSkipListSet<>();
    private transient List<Token> tokens;
    private transient boolean valid;

    public Topic(String topic) {
        this.topic = topic;
    }

    Topic(List<Token> tokens) {
        this.tokens = tokens;
        List<String> strTokens = tokens.stream().map(Token::toString).collect(Collectors.toList());
        this.topic = String.join("/", strTokens);
        this.valid = true;
    }

    /**
     * Factory method
     */
    public static Topic asTopic(String s) {
        return new Topic(s);
    }

    public void subscribe(String clientIdentifier) {
        subscribes.add(clientIdentifier);
    }

    public void unSubscribe(String clientIdentifier) {
        boolean suc = subscribes.remove(clientIdentifier);
        LOGGER.info("clientId:{} unsubscribe topic:{} {}", clientIdentifier, topic, suc ? "success" : "ignore");
    }

    public Set<String> getSubscribes() {
        return subscribes;
    }

    public List<Token> getTokens() {
        if (tokens == null) {
            try {
                tokens = parseTopic(topic);
                valid = true;
            } catch (ParseException e) {
                valid = false;
                e.printStackTrace();
            }
        }

        return tokens;
    }

    private List<Token> parseTopic(String topic) throws ParseException {
        List<Token> res = new ArrayList<>();
        String[] splitted = topic.split("/");

        if (splitted.length == 0) {
            res.add(Token.EMPTY);
        }

        if (topic.endsWith("/")) {
            // Add a fictious space
            String[] newSplitted = new String[splitted.length + 1];
            System.arraycopy(splitted, 0, newSplitted, 0, splitted.length);
            newSplitted[splitted.length] = "";
            splitted = newSplitted;
        }

        for (int i = 0; i < splitted.length; i++) {
            String s = splitted[i];
            if (s.isEmpty()) {
                // if (i != 0) {
                // throw new ParseException("Bad format of topic, expetec topic name between
                // separators", i);
                // }
                res.add(Token.EMPTY);
            } else if (s.equals("#")) {
                // check that multi is the last symbol
                if (i != splitted.length - 1) {
                    throw new ParseException(
                            "Bad format of topic, the multi symbol (#) has to be the last one after a separator",
                            i);
                }
                res.add(Token.MULTI);
            } else if (s.contains("#")) {
                throw new ParseException("Bad format of topic, invalid subtopic name: " + s, i);
            } else if (s.equals("+")) {
                res.add(Token.SINGLE);
            } else if (s.contains("+")) {
                throw new ParseException("Bad format of topic, invalid subtopic name: " + s, i);
            } else {
                res.add(new Token(s));
            }
        }

        return res;
    }

    public Token headToken() {
        final List<Token> tokens = getTokens();
        if (tokens.isEmpty()) {
            //TODO UGLY use Optional
            return null;
        }
        return tokens.get(0);
    }

    public boolean isEmpty() {
        final List<Token> tokens = getTokens();
        return tokens == null || tokens.isEmpty();
    }

    /**
     * @return a new Topic corresponding to this less than the head token
     */
    public Topic exceptHeadToken() {
        List<Token> tokens = getTokens();
        if (tokens.isEmpty()) {
            return new Topic(Collections.emptyList());
        }
        List<Token> tokensCopy = new ArrayList<>(tokens);
        tokensCopy.remove(0);
        return new Topic(tokensCopy);
    }

    public boolean isValid() {
        if (tokens == null) {
            getTokens();
        }

        return valid;
    }

    /**
     * Verify if the 2 topics matching respecting the rules of MQTT Appendix A
     *
     * @param subscriptionTopic the topic filter of the subscription
     * @return true if the two topics match.
     */
    // TODO reimplement with iterators or with queues
    public boolean match(Topic subscriptionTopic) {
        List<Token> msgTokens = getTokens();
        List<Token> subscriptionTokens = subscriptionTopic.getTokens();
        int i = 0;
        for (; i < subscriptionTokens.size(); i++) {
            Token subToken = subscriptionTokens.get(i);
            if (subToken != Token.MULTI && subToken != Token.SINGLE) {
                if (i >= msgTokens.size()) {
                    return false;
                }
                Token msgToken = msgTokens.get(i);
                if (!msgToken.equals(subToken)) {
                    return false;
                }
            } else {
                if (subToken == Token.MULTI) {
                    return true;
                }
                if (subToken == Token.SINGLE) {
                    // skip a step forward
                }
            }
        }
        // if last token was a SINGLE then treat it as an empty
        // if (subToken == Token.SINGLE && (i - msgTokens.size() == 1)) {
        // i--;
        // }
        return i == msgTokens.size();
    }

    public String getTopic() {
        return topic;
    }

    @Override
    public String toString() {
        return topic;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Topic other = (Topic) obj;

        return Objects.equals(this.topic, other.topic);
    }

    @Override
    public int hashCode() {
        return topic.hashCode();
    }

}
