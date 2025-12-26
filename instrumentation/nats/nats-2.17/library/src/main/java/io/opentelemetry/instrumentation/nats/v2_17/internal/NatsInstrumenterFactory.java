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
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NatsInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.nats-2.17";

  public static Instrumenter<NatsRequest, NatsRequest> createProducerInstrumenter(
      OpenTelemetry openTelemetry, List<String> capturedHeaders, List<String> temporaryPrefixes) {
    return Instrumenter.<NatsRequest, NatsRequest>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(
                new NatsRequestMessagingAttributesGetter(temporaryPrefixes),
                MessageOperation.PUBLISH))
        .addAttributesExtractor(
            MessagingAttributesExtractor.builder(
                    new NatsRequestMessagingAttributesGetter(temporaryPrefixes),
                    MessageOperation.PUBLISH)
                .setCapturedHeaders(capturedHeaders)
                .build())
        .buildProducerInstrumenter(NatsRequestTextMapSetter.INSTANCE);
  }

  public static Instrumenter<NatsRequest, Void> createConsumerProcessInstrumenter(
      OpenTelemetry openTelemetry, List<String> capturedHeaders, List<String> temporaryPrefixes) {
    InstrumenterBuilder<NatsRequest, Void> builder =
        Instrumenter.<NatsRequest, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    new NatsRequestMessagingAttributesGetter(temporaryPrefixes),
                    MessageOperation.PROCESS))
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(
                        new NatsRequestMessagingAttributesGetter(temporaryPrefixes),
                        MessageOperation.PROCESS)
                    .setCapturedHeaders(capturedHeaders)
                    .build());

    return builder.buildConsumerInstrumenter(NatsRequestTextMapGetter.INSTANCE);
  }

  private NatsInstrumenterFactory() {}
}
