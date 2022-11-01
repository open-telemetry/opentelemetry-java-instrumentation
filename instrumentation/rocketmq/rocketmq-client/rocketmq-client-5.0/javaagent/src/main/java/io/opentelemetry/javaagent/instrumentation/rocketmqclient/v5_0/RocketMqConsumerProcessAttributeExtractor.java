/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_ROCKETMQ_CLIENT_GROUP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.ArrayList;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;

enum RocketMqConsumerProcessAttributeExtractor
    implements AttributesExtractor<MessageView, ConsumeResult> {
  INSTANCE;

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      MessageView messageView,
      @Nullable ConsumeResult consumeResult,
      @Nullable Throwable error) {}

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, MessageView messageView) {
    messageView.getTag().ifPresent(s -> attributes.put(MESSAGING_ROCKETMQ_MESSAGE_TAG, s));
    attributes.put(MESSAGING_ROCKETMQ_MESSAGE_KEYS, new ArrayList<>(messageView.getKeys()));
    attributes.put(
        MESSAGING_ROCKETMQ_CLIENT_GROUP, VirtualFieldStore.getConsumerGroupByMessage(messageView));
  }
}
