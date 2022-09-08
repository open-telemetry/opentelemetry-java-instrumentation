/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistryBuilder;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

class MicrometerTestingExtension implements AfterEachCallback, BeforeEachCallback {

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(MicrometerTestingExtension.class);

  private final InstrumentationExtension testing;

  MicrometerTestingExtension(InstrumentationExtension testing) {
    this.testing = testing;
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    ExtensionContext.Store store = context.getStore(NAMESPACE);

    MeterRegistry otelMeterRegistry =
        configureOtelRegistry(OpenTelemetryMeterRegistry.builder(testing.getOpenTelemetry()))
            .build();
    configureMeterRegistry(otelMeterRegistry);

    store.put(MeterRegistry.class, otelMeterRegistry);

    Metrics.addRegistry(otelMeterRegistry);
  }

  @Override
  public void afterEach(ExtensionContext context) {
    ExtensionContext.Store store = context.getStore(NAMESPACE);
    MeterRegistry otelMeterRegistry = store.get(MeterRegistry.class, MeterRegistry.class);

    Metrics.removeRegistry(otelMeterRegistry);

    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  OpenTelemetryMeterRegistryBuilder configureOtelRegistry(
      OpenTelemetryMeterRegistryBuilder registry) {
    return registry;
  }

  MeterRegistry configureMeterRegistry(MeterRegistry registry) {
    return registry;
  }
}
