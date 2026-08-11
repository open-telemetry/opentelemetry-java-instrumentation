/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class RocketMqAckAttributeExtractor implements AttributesExtractor<RocketMqAckRequest, Void> {

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, RocketMqAckRequest request) {
    attributes.put(MESSAGING_CONSUMER_GROUP_NAME, request.getConsumerGroup());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      RocketMqAckRequest request,
      @Nullable Void response,
      @Nullable Throwable error) {}
}
