/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.JmsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageWithDestination;

public class SpringJmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-jms-6.0";

  private static final boolean RECEIVE_TELEMETRY_ENABLED =
      ExperimentalConfig.get().messagingReceiveInstrumentationEnabled();
  private static final Instrumenter<MessageWithDestination, Void> listenerInstrumenter;
  private static final Instrumenter<MessageWithDestination, Void> receiveInstrumenter;

  static {
    JmsInstrumenterFactory factory =
        new JmsInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
            .setMessagingReceiveTelemetryEnabled(RECEIVE_TELEMETRY_ENABLED);

    listenerInstrumenter = factory.createConsumerProcessInstrumenter(true);
    receiveInstrumenter = factory.createConsumerReceiveInstrumenter();
  }

  public static boolean isReceiveTelemetryEnabled() {
    return RECEIVE_TELEMETRY_ENABLED;
  }

  public static Instrumenter<MessageWithDestination, Void> listenerInstrumenter() {
    return listenerInstrumenter;
  }

  public static Instrumenter<MessageWithDestination, Void> receiveInstrumenter() {
    return receiveInstrumenter;
  }

  private SpringJmsSingletons() {}
}
