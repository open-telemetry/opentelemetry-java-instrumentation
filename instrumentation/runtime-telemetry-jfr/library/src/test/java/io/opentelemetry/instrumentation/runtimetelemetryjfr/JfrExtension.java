/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class JfrExtension implements BeforeEachCallback, AfterEachCallback {

  private final Consumer<JfrTelemetryBuilder> builderConsumer;

  private SdkMeterProvider meterProvider;
  private InMemoryMetricReader metricReader;
  private JfrTelemetry jfrTelemetry;

  public JfrExtension(Consumer<JfrTelemetryBuilder> builderConsumer) {
    this.builderConsumer = builderConsumer;
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    try {
      Class.forName("jdk.jfr.consumer.RecordingStream");
    } catch (ClassNotFoundException exception) {
      Assumptions.abort("JFR not present");
    }

    metricReader = InMemoryMetricReader.create();
    meterProvider = SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();
    JfrTelemetryBuilder builder = JfrTelemetry.builder(sdk);
    builderConsumer.accept(builder);
    jfrTelemetry = builder.build();
  }

  @Override
  public void afterEach(ExtensionContext context) {
    if (meterProvider != null) {
      meterProvider.close();
    }
    if (jfrTelemetry != null) {
      jfrTelemetry.close();
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
