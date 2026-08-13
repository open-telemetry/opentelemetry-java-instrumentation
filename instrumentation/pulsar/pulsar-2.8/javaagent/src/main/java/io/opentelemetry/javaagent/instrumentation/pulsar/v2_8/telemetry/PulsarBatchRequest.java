/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.parseUrl;
import static java.util.Collections.emptyList;
import static org.apache.pulsar.client.impl.MultiTopicsConsumerImpl.DUMMY_TOPIC_NAME_PREFIX;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Messages;

public class PulsarBatchRequest extends BasePulsarRequest {
  @Nullable private final Messages<?> messages;
  @Nullable private final PulsarBatchRecordAttributes batchRecordAttributes;
  private final boolean generatedDestinationName;

  public static PulsarBatchRequest create(
      @Nullable Messages<?> messages, @Nullable String url, Consumer<?> consumer) {
    String topicName =
        // messages are missing when the receive failed, use the topic the consumer is subscribed to
        messages != null ? getTopicName(messages) : consumer.getTopic();
    return new PulsarBatchRequest(
        messages,
        topicName,
        emitStableMessagingSemconv() && messages != null
            ? PulsarBatchRecordAttributes.create(messages)
            : null,
        messages == null && topicName.startsWith(DUMMY_TOPIC_NAME_PREFIX),
        parseUrl(url),
        consumer.getSubscription());
  }

  private PulsarBatchRequest(
      @Nullable Messages<?> messages,
      String topicName,
      @Nullable PulsarBatchRecordAttributes batchRecordAttributes,
      boolean generatedDestinationName,
      @Nullable UrlData urlData,
      @Nullable String subscription) {
    super(topicName, urlData, subscription);
    this.messages = messages;
    this.batchRecordAttributes = batchRecordAttributes;
    this.generatedDestinationName = generatedDestinationName;
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
        return stripPartitionSuffix(topicName);
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

  boolean hasGeneratedDestinationName() {
    return generatedDestinationName;
  }

  /**
   * Returns the split of the per-message attributes into the ones shared by the whole batch and the
   * ones that vary, or {@code null} when the stable messaging semantic conventions are not emitted
   * and the batch is therefore not split.
   */
  @Nullable
  PulsarBatchRecordAttributes getBatchRecordAttributes() {
    return batchRecordAttributes;
  }
}
