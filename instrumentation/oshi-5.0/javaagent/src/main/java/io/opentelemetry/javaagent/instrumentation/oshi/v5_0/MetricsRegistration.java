/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi.v5_0;

import static io.opentelemetry.instrumentation.oshi.v5_0.internal.SchemaUrls.V1_19_0;
import static io.opentelemetry.semconv.SchemaUrls.V1_44_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.oshi.v5_0.ProcessMetrics;
import io.opentelemetry.instrumentation.oshi.v5_0.internal.SystemMetricsInternal;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

public class MetricsRegistration {

  private static final String VERSION_LOOKUP_NAME = "io.opentelemetry.oshi-5.0";
  private static final String INSTRUMENTATION_NAME =
      AgentCommonConfig.get().isV3Preview() ? VERSION_LOOKUP_NAME : "io.opentelemetry.oshi";

  private static final AtomicBoolean registered = new AtomicBoolean();

  @SuppressWarnings("deprecation") // ProcessMetrics keeps its caller-owned meter
  public static void register() {
    if (registered.compareAndSet(false, true)) {
      boolean preview = AgentCommonConfig.get().isV3Preview();
      List<AutoCloseable> observables = new ArrayList<>();
      observables.addAll(
          SystemMetricsInternal.registerObservers(
              buildMeter(preview ? V1_44_0 : V1_19_0), preview));

      // ProcessMetrics don't follow the spec
      if (DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "oshi")
          .get("experimental_metrics/development")
          .getBoolean("enabled", false)) {
        observables.addAll(ProcessMetrics.registerObservers(buildMeter(null)));
      }
      Thread cleanupTelemetry = new Thread(() -> MetricsRegistration.closeObservables(observables));
      Runtime.getRuntime().addShutdownHook(cleanupTelemetry);
    }
  }

  private static Meter buildMeter(@Nullable String schemaUrl) {
    MeterBuilder meterBuilder =
        GlobalOpenTelemetry.get().getMeterProvider().meterBuilder(INSTRUMENTATION_NAME);
    if (schemaUrl != null) {
      meterBuilder.setSchemaUrl(schemaUrl);
    }
    String version = EmbeddedInstrumentationProperties.findVersion(VERSION_LOOKUP_NAME);
    if (version != null) {
      meterBuilder.setInstrumentationVersion(version);
    }
    return meterBuilder.build();
  }

  private static void closeObservables(List<AutoCloseable> observables) {
    observables.forEach(MetricsRegistration::closeObservable);
  }

  private static void closeObservable(AutoCloseable observable) {
    try {
      observable.close();
    } catch (Exception e) {
      throw new IllegalStateException("Error occurred closing observable", e);
    }
  }

  private MetricsRegistration() {}
}
