/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.parseUrl;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Messages;

public class PulsarBatchRequest extends BasePulsarRequest {
  private final Messages<?> messages;
  @Nullable private final PulsarBatchRecordAttributes batchRecordAttributes;

  public static PulsarBatchRequest create(
      Messages<?> messages, @Nullable String url, Consumer<?> consumer) {
    return new PulsarBatchRequest(
        messages,
        getTopicName(messages),
        emitStableMessagingSemconv() ? PulsarBatchRecordAttributes.create(messages) : null,
        parseUrl(url),
        consumer.getSubscription());
  }

  private PulsarBatchRequest(
      Messages<?> messages,
      String topicName,
      @Nullable PulsarBatchRecordAttributes batchRecordAttributes,
      @Nullable UrlData urlData,
      @Nullable String subscription) {
    super(topicName, urlData, subscription);
    this.messages = messages;
    this.batchRecordAttributes = batchRecordAttributes;
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

  public Messages<?> getMessages() {
    return messages;
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
