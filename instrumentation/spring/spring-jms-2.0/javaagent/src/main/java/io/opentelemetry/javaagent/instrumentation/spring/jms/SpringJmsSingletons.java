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
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.jms.JmsMessageAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.jms.MessagePropertyGetter;
import io.opentelemetry.javaagent.instrumentation.jms.MessageWithDestination;

public final class SpringJmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-jms-2.0";

  private static final Instrumenter<MessageWithDestination, Void> LISTENER_INSTRUMENTER =
      buildListenerInstrumenter();

  private static Instrumenter<MessageWithDestination, Void> buildListenerInstrumenter() {
    JmsMessageAttributesGetter getter = JmsMessageAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.PROCESS;

    return Instrumenter.<MessageWithDestination, Void>builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(
            MessagingAttributesExtractor.builder(getter, operation)
                .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
                .build())
        .buildConsumerInstrumenter(MessagePropertyGetter.INSTANCE);
  }

  public static Instrumenter<MessageWithDestination, Void> listenerInstrumenter() {
    return LISTENER_INSTRUMENTER;
  }

  private SpringJmsSingletons() {}
}
