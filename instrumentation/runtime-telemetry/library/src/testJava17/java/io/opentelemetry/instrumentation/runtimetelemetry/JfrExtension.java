/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrConfig;
import io.opentelemetry.instrumentation.testing.internal.MetaDataCollector;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final Map<InstrumentationScopeInfo, Map<String, MetricData>> metricsByScope =
      new HashMap<>();
  private final Set<InstrumentationScopeInfo> instrumentationScopes = new HashSet<>();

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
  public void afterEach(ExtensionContext context) throws IOException {
    if (meterProvider != null) {
      meterProvider.close();
    }
    if (runtimeMetrics != null) {
      runtimeMetrics.close();
    }

    // Generates files in a `.telemetry` directory within the instrumentation module with all
    // captured emitted metadata to be used by the instrumentation-docs Doc generator.
    if (Boolean.getBoolean("collectMetadata")) {
      String path = new File("").getAbsolutePath();

      MetaDataCollector.writeTelemetryToFiles(
          path, metricsByScope, emptyMap(), instrumentationScopes);
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
              if (Boolean.getBoolean("collectMetadata")) {
                collectEmittedMetrics(metrics.stream().toList());
              }
            });
  }

  private void collectEmittedMetrics(List<MetricData> metrics) {
    for (MetricData metric : metrics) {
      Map<String, MetricData> scopeMap =
          this.metricsByScope.computeIfAbsent(
              metric.getInstrumentationScopeInfo(), m -> new HashMap<>());

      if (!scopeMap.containsKey(metric.getName())) {
        scopeMap.put(metric.getName(), metric);
      }

      InstrumentationScopeInfo scopeInfo = metric.getInstrumentationScopeInfo();
      if (!scopeInfo.getName().equals("test")) {
        instrumentationScopes.add(scopeInfo);
      }
    }
  }
}
