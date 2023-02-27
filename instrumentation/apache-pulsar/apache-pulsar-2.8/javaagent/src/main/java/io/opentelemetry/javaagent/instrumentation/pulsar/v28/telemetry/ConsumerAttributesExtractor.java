/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v28.telemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Message;

enum ConsumerAttributesExtractor implements AttributesExtractor<Message<?>, Attributes> {
  INSTANCE;

  private ConsumerAttributesExtractor() {}

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Message<?> message) {}

  @Override
  public void onEnd(
      AttributesBuilder attributesBuilder,
      Context context,
      Message<?> message,
      @Nullable Attributes attributes,
      @Nullable Throwable error) {
    if (attributes != null && !attributes.isEmpty()) {
      attributesBuilder.putAll(attributes);
    }

    if (message.getTopicName() != null) {
      attributesBuilder.put(SemanticAttributes.MESSAGING_DESTINATION_NAME, message.getTopicName());
    }
  }
}
