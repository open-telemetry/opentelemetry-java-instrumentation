/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.parseUrl;
import static java.util.Collections.emptyList;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.common.naming.TopicName;

public class PulsarBatchRequest extends BasePulsarRequest {
  @Nullable private final Messages<?> messages;

  public static PulsarBatchRequest create(
      @Nullable Messages<?> messages, @Nullable String url, Consumer<?> consumer) {
    return new PulsarBatchRequest(
        messages,
        // messages are missing when the receive failed, use the topic the consumer is subscribed to
        messages != null ? getTopicName(messages) : consumer.getTopic(),
        parseUrl(url),
        consumer.getSubscription());
  }

  private PulsarBatchRequest(
      @Nullable Messages<?> messages,
      String destination,
      @Nullable UrlData urlData,
      @Nullable String subscription) {
    super(destination, urlData, subscription);
    this.messages = messages;
  }

  private static String getTopicName(Messages<?> messages) {
    String topicName = null;
    for (Message<?> message : messages) {
      String name = message.getTopicName();
      if (topicName == null) {
        topicName = name;
      } else if (!topicName.equals(name)) {
        // this is a partitioned topic
        // persistent://public/default/test-partition-0 persistent://public/default/test-partition-1
        // return persistent://public/default/test
        return TopicName.get(topicName).getPartitionedTopicName();
      }
    }
    return topicName;
  }

  public Iterable<? extends Message<?>> getMessages() {
    return messages != null ? messages : emptyList();
  }

  public long getMessageCount() {
    return messages != null ? messages.size() : 0;
  }
}
