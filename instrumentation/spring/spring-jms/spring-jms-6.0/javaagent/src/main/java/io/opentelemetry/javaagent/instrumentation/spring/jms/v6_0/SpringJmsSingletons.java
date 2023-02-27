/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.jms.JmsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jms.MessageWithDestination;

public final class SpringJmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-jms-6.0";

  private static final Instrumenter<MessageWithDestination, Void> LISTENER_INSTRUMENTER =
      new JmsInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
          .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
          .createConsumerProcessInstrumenter();

  public static Instrumenter<MessageWithDestination, Void> listenerInstrumenter() {
    return LISTENER_INSTRUMENTER;
  }

  private SpringJmsSingletons() {}
}
