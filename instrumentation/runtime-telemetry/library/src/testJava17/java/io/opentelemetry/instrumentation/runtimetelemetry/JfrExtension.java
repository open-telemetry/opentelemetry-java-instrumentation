/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrConfig;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import java.util.function.Consumer;
import jdk.jfr.FlightRecorder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class JfrExtension implements BeforeEachCallback, AfterEachCallback {

  private final Consumer<JfrConfig> jfrConfigConsumer;

  private SdkMeterProvider meterProvider;
  private InMemoryMetricReader metricReader;
  private RuntimeTelemetry runtimeMetrics;

  public JfrExtension(Consumer<JfrConfig> jfrConfigConsumer) {
    this.jfrConfigConsumer = jfrConfigConsumer;
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
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(sdk);
    jfrConfigConsumer.accept(builder.getJfrConfig());
    runtimeMetrics = builder.build();
    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeMetrics.getJfrTelemetry();
    if (jfrRuntimeMetrics != null) {
      jfrRuntimeMetrics.getStartUpLatch().await(30, SECONDS);
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
