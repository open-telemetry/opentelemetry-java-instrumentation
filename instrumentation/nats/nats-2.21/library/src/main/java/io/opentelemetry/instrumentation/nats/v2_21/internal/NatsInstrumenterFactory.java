/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21.internal;

import io.nats.client.Message;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.", or "This class is internal and experimental. Its APIs are unstable and can change at
 * any time. Its APIs (or a version of them) may be promoted to the public stable API in the future,
 * but no guarantees are made.
 */
public final class NatsInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.nats-2.21";

  public static final SpanNameExtractor<Message> PRODUCER_SPAN_NAME_EXTRACTOR =
      MessagingSpanNameExtractor.create(
          MessageMessagingAttributesGetter.INSTANCE, MessageOperation.PUBLISH);

  public static final AttributesExtractor<Message, Void> PUBLISH_ATTRIBUTES_EXTRACTOR =
      MessagingAttributesExtractor.create(
          MessageMessagingAttributesGetter.INSTANCE, MessageOperation.PUBLISH);

  public static Instrumenter<Message, Void> createProducerInstrumenter(
      OpenTelemetry openTelemetry) {
    return Instrumenter.<Message, Void>builder(
            openTelemetry, INSTRUMENTATION_NAME, PRODUCER_SPAN_NAME_EXTRACTOR)
        .addAttributesExtractor(PUBLISH_ATTRIBUTES_EXTRACTOR)
        .buildProducerInstrumenter(MessageTextMapSetter.INSTANCE);
  }

  private NatsInstrumenterFactory() {}
}
