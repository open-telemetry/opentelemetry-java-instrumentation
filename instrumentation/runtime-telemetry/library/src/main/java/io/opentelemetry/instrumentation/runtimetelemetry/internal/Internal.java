/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetryBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class Internal {

  @Nullable private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean> setDisableJmx;

  /** Disables all JMX-based metrics. Visible for testing. */
  public static void setDisableJmx(RuntimeTelemetryBuilder builder, boolean disable) {
    if (setDisableJmx != null) {
      setDisableJmx.accept(builder, disable);
    }
  }

  public static void internalSetDisableJmx(BiConsumer<RuntimeTelemetryBuilder, Boolean> callback) {
    Internal.setDisableJmx = callback;
  }

  /**
   * Configures and builds a {@link RuntimeTelemetry} instance based on the provided configuration.
   *
   * @param openTelemetry the OpenTelemetry instance
   * @param defaultEnabled whether instrumentation is enabled by default
   * @return the configured RuntimeTelemetry, or null if runtime telemetry is disabled
   */
  @Nullable
  public static RuntimeTelemetry configure(OpenTelemetry openTelemetry, boolean defaultEnabled) {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry");
    if (!config.getBoolean("enabled", defaultEnabled)) {
      return null;
    }

    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(openTelemetry);
    Experimental.setEmitExperimentalMetrics(
        builder, config.getBoolean("emit_experimental_metrics/development", false));
    Experimental.setEmitExperimentalJfrMetrics(
        builder, config.getBoolean("emit_experimental_jfr_metrics/development", false));

    DeclarativeConfigProperties jfrMetrics = config.get("jfr_metrics/development");
    List<String> included = jfrMetrics.getScalarList("included", String.class, emptyList());
    List<String> excluded = jfrMetrics.getScalarList("excluded", String.class, emptyList());
    Experimental.setJfrMetrics(
        builder, IncludeExclude.builder().setIncluded(included).setExcluded(excluded).build());
    return builder.build();
  }

  private Internal() {}
}
