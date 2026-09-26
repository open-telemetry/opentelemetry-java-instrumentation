/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quartz.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.quartz.v2_0.QuartzTelemetry;
import java.util.logging.Logger;

public class QuartzSingletons {

  private static final Logger logger = Logger.getLogger(QuartzSingletons.class.getName());

  private static final QuartzTelemetry telemetry =
      QuartzTelemetry.builder(GlobalOpenTelemetry.get())
          .setEmitExperimentalTelemetry(
              emitExperimentalTelemetry(
                  DeclarativeConfigUtil.getInstrumentationConfig(
                      GlobalOpenTelemetry.get(), "quartz")))
          .build();

  private static boolean emitExperimentalTelemetry(DeclarativeConfigProperties config) {
    Boolean emitExperimentalTelemetry =
        config.getBoolean("emit_experimental_telemetry/development");
    // Support the deprecated config key until 3.0.
    Boolean deprecatedExperimentalSpanAttributes =
        config.getBoolean("experimental_span_attributes/development");
    if (deprecatedExperimentalSpanAttributes != null) {
      logger.warning(
          "The otel.instrumentation.quartz.experimental-span-attributes setting or equivalent"
              + " declarative configuration is deprecated and will be removed in 3.0. Use"
              + " otel.instrumentation.quartz.emit-experimental-telemetry or equivalent"
              + " declarative configuration instead.");
    }

    if (emitExperimentalTelemetry != null) {
      return emitExperimentalTelemetry;
    }
    return deprecatedExperimentalSpanAttributes != null
        ? deprecatedExperimentalSpanAttributes
        : false;
  }

  public static QuartzTelemetry telemetry() {
    return telemetry;
  }

  private QuartzSingletons() {}
}
