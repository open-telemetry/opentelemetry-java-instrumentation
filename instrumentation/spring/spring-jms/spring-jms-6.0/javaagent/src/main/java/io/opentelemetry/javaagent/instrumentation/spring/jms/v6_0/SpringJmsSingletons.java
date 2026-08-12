/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.JmsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageWithDestination;

public class SpringJmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-jms-6.0";

  public static final boolean RECEIVE_TELEMETRY_ENABLED = receiveTelemetryEnabled();
  private static final Instrumenter<MessageWithDestination, Void> listenerInstrumenter;
  private static final Instrumenter<MessageWithDestination, Void> receiveInstrumenter;

  static {
    JmsInstrumenterFactory factory =
        new JmsInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders());
    Boolean receiveTelemetryEnabled =
        ExperimentalConfig.get().messagingReceiveInstrumentationEnabled();
    if (receiveTelemetryEnabled != null) {
      factory.setMessagingReceiveTelemetryEnabled(receiveTelemetryEnabled);
    }

    listenerInstrumenter = factory.createConsumerProcessInstrumenter(RECEIVE_TELEMETRY_ENABLED);
    receiveInstrumenter = factory.createConsumerReceiveInstrumenter();
  }

  public static Instrumenter<MessageWithDestination, Void> listenerInstrumenter() {
    return listenerInstrumenter;
  }

  public static Instrumenter<MessageWithDestination, Void> receiveInstrumenter() {
    return receiveInstrumenter;
  }

  private static boolean receiveTelemetryEnabled() {
    Boolean configured = ExperimentalConfig.get().messagingReceiveInstrumentationEnabled();
    return configured != null ? configured : emitStableMessagingSemconv();
  }

  private SpringJmsSingletons() {}
}
