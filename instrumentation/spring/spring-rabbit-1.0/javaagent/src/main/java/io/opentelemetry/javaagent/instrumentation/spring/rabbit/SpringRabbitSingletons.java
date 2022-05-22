/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import org.springframework.amqp.core.Message;

public final class SpringRabbitSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-rabbit-1.0";

  private static final Instrumenter<Message, Void> INSTRUMENTER;

  static {
    SpringRabbitMessageAttributesGetter getter = SpringRabbitMessageAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.PROCESS;

    INSTRUMENTER =
        Instrumenter.<Message, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operation))
            .addAttributesExtractor(MessagingAttributesExtractor.create(getter, operation))
            .newConsumerInstrumenter(MessageHeaderGetter.INSTANCE);
  }

  public static Instrumenter<Message, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private SpringRabbitSingletons() {}
}
