/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import org.apache.rocketmq.common.message.MessageExt;

enum RocketMqConsumerExperimentalAttributeExtractor
    implements AttributesExtractor<MessageExt, Void> {
  INSTANCE;

  private static final AttributeKey<Long> MESSAGING_ROCKETMQ_QUEUE_ID =
      AttributeKey.longKey("messaging.rocketmq.queue_id");
  private static final AttributeKey<Long> MESSAGING_ROCKETMQ_QUEUE_OFFSET =
      AttributeKey.longKey("messaging.rocketmq.queue_offset");
  private static final AttributeKey<String> MESSAGING_ROCKETMQ_BROKER_ADDRESS =
      AttributeKey.stringKey("messaging.rocketmq.broker_address");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, MessageExt msg) {
    String tags = msg.getTags();
    if (tags != null) {
      attributes.put(SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG, tags);
    }
    attributes.put(MESSAGING_ROCKETMQ_QUEUE_ID, msg.getQueueId());
    attributes.put(MESSAGING_ROCKETMQ_QUEUE_OFFSET, msg.getQueueOffset());
    SocketAddress storeHost = msg.getStoreHost();
    if (storeHost != null) {
      attributes.put(MESSAGING_ROCKETMQ_BROKER_ADDRESS, getBrokerHost(storeHost));
    }
  }

  private static String getBrokerHost(SocketAddress storeHost) {
    return storeHost.toString().replace("/", "");
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      MessageExt consumeMessageContext,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
