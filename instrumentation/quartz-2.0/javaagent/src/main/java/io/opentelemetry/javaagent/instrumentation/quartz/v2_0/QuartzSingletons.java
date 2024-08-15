/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quartz.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.quartz.v2_0.QuartzTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class QuartzSingletons {

  public static final QuartzTelemetry TELEMETRY =
      QuartzTelemetry.builder(GlobalOpenTelemetry.get())
          .setCaptureExperimentalSpanAttributes(
              AgentInstrumentationConfig.get()
                  .getBoolean("otel.instrumentation.quartz.experimental-span-attributes", false))
          .build();

  private QuartzSingletons() {}
}
