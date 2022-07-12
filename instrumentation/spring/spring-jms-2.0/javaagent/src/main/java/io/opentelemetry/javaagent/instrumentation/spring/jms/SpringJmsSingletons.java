/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;

public final class SpringJmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-jms-2.0";

  private static final Instrumenter<MessageWithDestination, Void> LISTENER_INSTRUMENTER =
      buildListenerInstrumenter();

  private static Instrumenter<MessageWithDestination, Void> buildListenerInstrumenter() {
    SpringJmsMessageAttributesGetter getter = SpringJmsMessageAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.PROCESS;

    return Instrumenter.<MessageWithDestination, Void>builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(MessagingAttributesExtractor.create(getter, operation))
        .newConsumerInstrumenter(MessagePropertyGetter.INSTANCE);
  }

  public static Instrumenter<MessageWithDestination, Void> listenerInstrumenter() {
    return LISTENER_INSTRUMENTER;
  }

  private SpringJmsSingletons() {}
}
