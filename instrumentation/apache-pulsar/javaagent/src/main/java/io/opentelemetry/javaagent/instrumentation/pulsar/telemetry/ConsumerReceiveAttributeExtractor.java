/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.telemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import org.apache.pulsar.client.api.Message;
import javax.annotation.Nullable;

class ConsumerReceiveAttributeExtractor
    implements AttributesExtractor<Message<?>, Attributes> {
  public static final ConsumerReceiveAttributeExtractor INSTANCE =
      new ConsumerReceiveAttributeExtractor();

  private ConsumerReceiveAttributeExtractor() {}

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Message<?> message) {

  }

  @Override
  public void onEnd(AttributesBuilder attributesBuilder, Context context, Message<?> message,
      @Nullable Attributes attributes, @Nullable Throwable error) {
    if (null != attributes) {
      attributesBuilder.putAll(attributes);
    }
  }
}
