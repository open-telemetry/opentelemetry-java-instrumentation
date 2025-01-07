/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import jdk.jfr.FlightRecorder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class JfrExtension implements BeforeEachCallback, AfterEachCallback {

  private final Consumer<RuntimeMetricsBuilder> builderConsumer;

  private SdkMeterProvider meterProvider;
  private InMemoryMetricReader metricReader;
  private RuntimeMetrics runtimeMetrics;

  public JfrExtension(Consumer<RuntimeMetricsBuilder> builderConsumer) {
    this.builderConsumer = builderConsumer;
  }

  @Override
  public void beforeEach(ExtensionContext context) throws InterruptedException {
    try {
      Class.forName("jdk.jfr.FlightRecorder");
    } catch (ClassNotFoundException exception) {
      Assumptions.abort("JFR not present");
    }
    Assumptions.assumeTrue(FlightRecorder.isAvailable(), "JFR not available");

    metricReader = InMemoryMetricReader.create();
    meterProvider = SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();
    RuntimeMetricsBuilder builder = RuntimeMetrics.builder(sdk);
    builderConsumer.accept(builder);
    runtimeMetrics = builder.build();
    RuntimeMetrics.JfrRuntimeMetrics jfrRuntimeMetrics = runtimeMetrics.getJfrRuntimeMetrics();
    if (jfrRuntimeMetrics != null) {
      jfrRuntimeMetrics.getStartUpLatch().await(30, TimeUnit.SECONDS);
    }
  }

  @Override
  public void afterEach(ExtensionContext context) {
    if (meterProvider != null) {
      meterProvider.close();
    }
    if (runtimeMetrics != null) {
      runtimeMetrics.close();
    }
  }

  @SafeVarargs
  protected final void waitAndAssertMetrics(Consumer<MetricAssert>... assertions) {
    await()
        .untilAsserted(
            () -> {
              Collection<MetricData> metrics = metricReader.collectAllMetrics();

              assertThat(metrics).isNotEmpty();

              for (Consumer<MetricAssert> assertion : assertions) {
                assertThat(metrics).anySatisfy(metric -> assertion.accept(assertThat(metric)));
              }
            });
  }
}
