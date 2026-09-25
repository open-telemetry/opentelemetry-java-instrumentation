/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
import static io.opentelemetry.instrumentation.oshi.v5_0.internal.SchemaUrls.V1_19_0;
import static io.opentelemetry.semconv.SchemaUrls.V1_44_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.oshi.v5_0.internal.SystemMetricsInternal;
import java.util.List;

/** System Metrics Utility. */
public final class SystemMetrics {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.oshi-5.0";

  /** Register observers for system metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    boolean preview = v3Preview(openTelemetry);
    return SystemMetricsInternal.registerObservers(buildMeter(openTelemetry, preview), preview);
  }

  /**
   * Like {@link #registerObservers(OpenTelemetry)}, but accepts a pre-built {@link Meter} and
   * retains the legacy metric conventions.
   *
   * @deprecated Use {@link #registerObservers(OpenTelemetry)} to select the metric conventions and
   *     schema URL from the supplied OpenTelemetry configuration.
   */
  @Deprecated
  public static List<AutoCloseable> registerObservers(Meter meter) {
    return SystemMetricsInternal.registerObservers(meter, false);
  }

  private static Meter buildMeter(OpenTelemetry openTelemetry, boolean preview) {
    MeterBuilder meterBuilder = openTelemetry.getMeterProvider().meterBuilder(INSTRUMENTATION_NAME);
    meterBuilder.setSchemaUrl(preview ? V1_44_0 : V1_19_0);
    String version = EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME);
    if (version != null) {
      meterBuilder.setInstrumentationVersion(version);
    }
    return meterBuilder.build();
  }

  private SystemMetrics() {}
}
