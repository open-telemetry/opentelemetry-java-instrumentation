/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NatsInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.nats-2.17";

  public static Instrumenter<NatsRequest, NatsRequest> createProducerInstrumenter(
      OpenTelemetry openTelemetry, List<String> capturedHeaders) {
    return Instrumenter.<NatsRequest, NatsRequest>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(
                NatsRequestMessagingAttributesGetter.VOID_INSTANCE, MessageOperation.PUBLISH))
        .addAttributesExtractor(
            MessagingAttributesExtractor.builder(
                    NatsRequestMessagingAttributesGetter.NATS_REQUEST_INSTANCE,
                    MessageOperation.PUBLISH)
                .setCapturedHeaders(capturedHeaders)
                .build())
        .buildProducerInstrumenter(NatsRequestTextMapSetter.INSTANCE);
  }

  public static Instrumenter<NatsRequest, Void> createConsumerReceiveInstrumenter(
      OpenTelemetry openTelemetry, boolean enabled, List<String> capturedHeaders) {
    return Instrumenter.<NatsRequest, Void>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(
                NatsRequestMessagingAttributesGetter.VOID_INSTANCE, MessageOperation.RECEIVE))
        .addAttributesExtractor(
            MessagingAttributesExtractor.builder(
                    NatsRequestMessagingAttributesGetter.VOID_INSTANCE, MessageOperation.RECEIVE)
                .setCapturedHeaders(capturedHeaders)
                .build())
        .setEnabled(enabled)
        .buildConsumerInstrumenter(NatsRequestTextMapGetter.INSTANCE);
  }

  public static Instrumenter<NatsRequest, Void> createConsumerProcessInstrumenter(
      OpenTelemetry openTelemetry,
      boolean messagingReceiveInstrumentationEnabled,
      List<String> capturedHeaders) {
    InstrumenterBuilder<NatsRequest, Void> builder =
        Instrumenter.<NatsRequest, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    NatsRequestMessagingAttributesGetter.VOID_INSTANCE, MessageOperation.PROCESS))
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(
                        NatsRequestMessagingAttributesGetter.VOID_INSTANCE,
                        MessageOperation.PROCESS)
                    .setCapturedHeaders(capturedHeaders)
                    .build());

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
