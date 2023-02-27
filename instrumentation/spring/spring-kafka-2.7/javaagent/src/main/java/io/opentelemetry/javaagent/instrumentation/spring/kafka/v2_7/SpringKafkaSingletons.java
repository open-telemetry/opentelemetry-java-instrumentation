/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

public final class SpringKafkaSingletons {

  private static final SpringKafkaTelemetry TELEMETRY =
      SpringKafkaTelemetry.builder(GlobalOpenTelemetry.get())
          .setCaptureExperimentalSpanAttributes(
              InstrumentationConfig.get()
                  .getBoolean("otel.instrumentation.kafka.experimental-span-attributes", false))
          .setMessagingReceiveInstrumentationEnabled(
              ExperimentalConfig.get().messagingReceiveInstrumentationEnabled())
          .build();

  public static SpringKafkaTelemetry telemetry() {
    return TELEMETRY;
  }

  private SpringKafkaSingletons() {}
}
