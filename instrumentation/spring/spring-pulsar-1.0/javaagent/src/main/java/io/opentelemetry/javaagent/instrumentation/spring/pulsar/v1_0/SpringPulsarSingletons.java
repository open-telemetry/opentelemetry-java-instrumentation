/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import org.apache.pulsar.client.api.Message;

public class SpringPulsarSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-pulsar-1.0";
  private static final Instrumenter<Message<?>, Void> instrumenter;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    SpringPulsarMessageAttributesGetter getter = new SpringPulsarMessageAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;
    boolean messagingReceiveInstrumentationEnabled =
        ExperimentalConfig.get().messagingReceiveInstrumentationEnabled();

    InstrumenterBuilder<Message<?>, Void> builder =
        Instrumenter.<Message<?>, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType))
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(getter, operationType)
                    .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
                    .build());
    setMessagingProcessExceptionEventExtractor(builder);
    instrumenter =
        MessagingProcessInstrumenterFactory.create(
            builder,
            openTelemetry.getPropagators().getTextMapPropagator(),
            new MessageHeaderGetter(),
            messagingReceiveInstrumentationEnabled);
  }

  public static Instrumenter<Message<?>, Void> instrumenter() {
    return instrumenter;
  }

  private SpringPulsarSingletons() {}
}
