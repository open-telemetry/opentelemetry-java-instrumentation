/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import org.apache.pulsar.client.api.Message;

public final class SpringPulsarSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-pulsar-1.0";
  private static final Instrumenter<Message<?>, Void> INSTRUMENTER;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    SpringPulsarMessageAttributesGetter getter = SpringPulsarMessageAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.PROCESS;
    boolean messagingReceiveInstrumentationEnabled =
        ExperimentalConfig.get().messagingReceiveInstrumentationEnabled();

    InstrumenterBuilder<Message<?>, Void> builder =
        Instrumenter.<Message<?>, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operation))
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(getter, operation)
                    .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
                    .build());
    if (messagingReceiveInstrumentationEnabled) {
      builder.addSpanLinksExtractor(
          new PropagatorBasedSpanLinksExtractor<>(
              openTelemetry.getPropagators().getTextMapPropagator(), MessageHeaderGetter.INSTANCE));
      INSTRUMENTER = builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else {
      INSTRUMENTER = builder.buildConsumerInstrumenter(MessageHeaderGetter.INSTANCE);
    }
  }

  public static Instrumenter<Message<?>, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private SpringPulsarSingletons() {}
}
