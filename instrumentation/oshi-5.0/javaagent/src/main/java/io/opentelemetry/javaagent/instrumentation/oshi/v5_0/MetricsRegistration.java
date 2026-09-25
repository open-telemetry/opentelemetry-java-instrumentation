/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi.v5_0;

import static io.opentelemetry.instrumentation.oshi.v5_0.internal.SchemaUrls.V1_19_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.oshi.v5_0.ProcessMetrics;
import io.opentelemetry.instrumentation.oshi.v5_0.SystemMetrics;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

public class MetricsRegistration {

  // version file is generated from the gradle module name; look it up explicitly so the legacy
  // scope name still resolves to a version
  private static final String VERSION_LOOKUP_NAME = "io.opentelemetry.oshi-5.0";
  // under v3-preview switch the emitted scope name to match the gradle module name, otherwise
  // keep the pre-rename scope so existing dashboards/filters on
  // otel.scope.name="io.opentelemetry.oshi" continue to work
  private static final String INSTRUMENTATION_NAME =
      AgentCommonConfig.get().isV3Preview() ? VERSION_LOOKUP_NAME : "io.opentelemetry.oshi";

  private static final AtomicBoolean registered = new AtomicBoolean();

  @SuppressWarnings("deprecation") // deprecated overloads keep the legacy scope by default
  public static void register() {
    if (registered.compareAndSet(false, true)) {
      List<AutoCloseable> observables = new ArrayList<>();
      observables.addAll(SystemMetrics.registerObservers(buildMeter(V1_19_0)));

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
