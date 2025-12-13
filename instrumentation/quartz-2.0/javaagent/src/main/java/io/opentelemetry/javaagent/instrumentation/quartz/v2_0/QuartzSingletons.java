/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quartz.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.quartz.v2_0.QuartzTelemetry;

public final class QuartzSingletons {

  public static final QuartzTelemetry TELEMETRY =
      QuartzTelemetry.builder(GlobalOpenTelemetry.get())
          .setCaptureExperimentalSpanAttributes(
              DeclarativeConfigUtil.getBoolean(
                      GlobalOpenTelemetry.get(), "java", "quartz", "experimental_span_attributes")
                  .orElse(false))
          .build();

  private QuartzSingletons() {}
}
