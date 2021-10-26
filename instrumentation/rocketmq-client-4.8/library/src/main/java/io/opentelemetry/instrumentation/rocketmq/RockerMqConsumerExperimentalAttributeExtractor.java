/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.apache.rocketmq.common.message.MessageExt;

class RockerMqConsumerExperimentalAttributeExtractor
    implements AttributesExtractor<MessageExt, Void> {
  private static final AttributeKey<String> MESSAGING_ROCKETMQ_TAGS =
      AttributeKey.stringKey("messaging.rocketmq.tags");
  private static final AttributeKey<Long> MESSAGING_ROCKETMQ_QUEUE_ID =
      AttributeKey.longKey("messaging.rocketmq.queue_id");
  private static final AttributeKey<Long> MESSAGING_ROCKETMQ_QUEUE_OFFSET =
      AttributeKey.longKey("messaging.rocketmq.queue_offset");
  private static final AttributeKey<String> MESSAGING_ROCKETMQ_BROKER_ADDRESS =
      AttributeKey.stringKey("messaging.rocketmq.broker_address");

  @Override
  public void onStart(AttributesBuilder attributes, MessageExt msg) {
    set(attributes, MESSAGING_ROCKETMQ_TAGS, msg.getTags());
    set(attributes, MESSAGING_ROCKETMQ_QUEUE_ID, (long) msg.getQueueId());
    set(attributes, MESSAGING_ROCKETMQ_QUEUE_OFFSET, msg.getQueueOffset());
    set(attributes, MESSAGING_ROCKETMQ_BROKER_ADDRESS, getBrokerHost(msg));
  }

  @Nullable
  private static String getBrokerHost(MessageExt msg) {
    if (msg.getStoreHost() != null) {
      return msg.getStoreHost().toString().replace("/", "");
    } else {
      return null;
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      MessageExt consumeMessageContext,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
