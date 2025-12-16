/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.jms.JmsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jms.MessageWithDestination;

public final class SpringJmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-jms-2.0";

  private static final boolean RECEIVE_TELEMETRY_ENABLED =
      DeclarativeConfigUtil.get(GlobalOpenTelemetry.get())
          .get("messaging")
          .get("receive_telemetry/development")
          .getBoolean("enabled", false);
  private static final Instrumenter<MessageWithDestination, Void> LISTENER_INSTRUMENTER;
  private static final Instrumenter<MessageWithDestination, Void> RECEIVE_INSTRUMENTER;

  static {
    JmsInstrumenterFactory factory =
        new JmsInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(
                DeclarativeConfigUtil.get(GlobalOpenTelemetry.get())
                    .get("messaging")
                    .getScalarList("capture_headers/development", String.class, emptyList()))
            .setMessagingReceiveInstrumentationEnabled(RECEIVE_TELEMETRY_ENABLED);

    LISTENER_INSTRUMENTER = factory.createConsumerProcessInstrumenter(true);
    RECEIVE_INSTRUMENTER = factory.createConsumerReceiveInstrumenter();
  }

  public static boolean isReceiveTelemetryEnabled() {
    return RECEIVE_TELEMETRY_ENABLED;
  }

  public static Instrumenter<MessageWithDestination, Void> listenerInstrumenter() {
    return LISTENER_INSTRUMENTER;
  }

  public static Instrumenter<MessageWithDestination, Void> receiveInstrumenter() {
    return RECEIVE_INSTRUMENTER;
  }

  private SpringJmsSingletons() {}
}
