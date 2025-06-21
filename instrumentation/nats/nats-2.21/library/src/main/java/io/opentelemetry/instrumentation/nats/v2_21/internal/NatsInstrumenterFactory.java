/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.", or "This class is internal and experimental. Its APIs are unstable and can change at
 * any time. Its APIs (or a version of them) may be promoted to the public stable API in the future,
 * but no guarantees are made.
 */
public final class NatsInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.nats-2.21";

  public static Instrumenter<NatsRequest, NatsRequest> createProducerInstrumenter(
      OpenTelemetry openTelemetry) {
    return Instrumenter.<NatsRequest, NatsRequest>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(
                NatsRequestMessagingAttributesGetter.VOID_INSTANCE, MessageOperation.PUBLISH))
        .addAttributesExtractor( // TODO capture headers
            MessagingAttributesExtractor.create(
                NatsRequestMessagingAttributesGetter.NATS_REQUEST_INSTANCE,
                MessageOperation.PUBLISH))
        .buildProducerInstrumenter(NatsRequestTextMapSetter.INSTANCE);
  }

  public static Instrumenter<NatsRequest, Void> createConsumerReceiveInstrumenter(
      OpenTelemetry openTelemetry, boolean enabled) {
    return Instrumenter.<NatsRequest, Void>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(
                NatsRequestMessagingAttributesGetter.VOID_INSTANCE, MessageOperation.RECEIVE))
        .addAttributesExtractor( // TODO capture headers
            MessagingAttributesExtractor.create(
                NatsRequestMessagingAttributesGetter.VOID_INSTANCE, MessageOperation.RECEIVE))
        .setEnabled(enabled)
        .buildConsumerInstrumenter(NatsRequestTextMapGetter.INSTANCE);
  }

  public static Instrumenter<NatsRequest, Void> createConsumerProcessInstrumenter(
      OpenTelemetry openTelemetry, boolean messagingReceiveInstrumentationEnabled) {
    InstrumenterBuilder<NatsRequest, Void> builder =
        Instrumenter.<NatsRequest, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    NatsRequestMessagingAttributesGetter.VOID_INSTANCE, MessageOperation.PROCESS))
            .addAttributesExtractor(
                // TODO capture headers
                MessagingAttributesExtractor.create(
                    NatsRequestMessagingAttributesGetter.VOID_INSTANCE, MessageOperation.PROCESS));

    if (messagingReceiveInstrumentationEnabled) {
      builder.addSpanLinksExtractor(
          new PropagatorBasedSpanLinksExtractor<>(
              openTelemetry.getPropagators().getTextMapPropagator(),
              NatsRequestTextMapGetter.INSTANCE));
      return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else {
      return builder.buildConsumerInstrumenter(NatsRequestTextMapGetter.INSTANCE);
    }
  }

  private NatsInstrumenterFactory() {}
}
