/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.parseUrl;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.common.naming.TopicName;

public final class PulsarBatchRequest extends BasePulsarRequest {
  private final Messages<?> messages;

  private PulsarBatchRequest(Messages<?> messages, String destination, UrlData urlData) {
    super(destination, urlData);
    this.messages = messages;
  }

  public static PulsarBatchRequest create(Messages<?> messages, String url) {
    return new PulsarBatchRequest(messages, getTopicName(messages), parseUrl(url));
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

  public Messages<?> getMessages() {
    return messages;
  }
}
