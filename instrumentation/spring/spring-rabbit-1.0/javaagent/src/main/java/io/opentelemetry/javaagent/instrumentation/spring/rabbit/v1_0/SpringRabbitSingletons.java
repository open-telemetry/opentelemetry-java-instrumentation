/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

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
import org.springframework.amqp.core.Message;

public class SpringRabbitSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-rabbit-1.0";

  private static final Instrumenter<Message, Void> instrumenter;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    SpringRabbitMessageAttributesGetter getter = new SpringRabbitMessageAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;

    InstrumenterBuilder<Message, Void> builder =
        Instrumenter.<Message, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType))
            .addAttributesExtractor(
                MessagingAttributesExtractor.builderForOperationType(getter, operationType)
                    .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
                    .build())
            .addAttributesExtractor(new SpringRabbitExtraAttributesExtractor());
    setMessagingProcessExceptionEventExtractor(builder);

    instrumenter =
        MessagingProcessInstrumenterFactory.create(
            builder,
            openTelemetry.getPropagators().getTextMapPropagator(),
            new MessageHeaderGetter(),
            false);
  }

  public static Instrumenter<Message, Void> instrumenter() {
    return instrumenter;
  }

  private SpringRabbitSingletons() {}
}
