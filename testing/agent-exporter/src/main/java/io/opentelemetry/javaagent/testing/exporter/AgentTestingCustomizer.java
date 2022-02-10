/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import static java.util.Objects.requireNonNull;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.MetricReaderFactory;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Duration;
import javax.annotation.Nullable;

@AutoService({AutoConfigurationCustomizerProvider.class, AgentListener.class})
public class AgentTestingCustomizer implements AutoConfigurationCustomizerProvider, AgentListener {

  static final AgentTestingSpanProcessor spanProcessor =
      new AgentTestingSpanProcessor(
          SimpleSpanProcessor.create(AgentTestingExporterFactory.spanExporter));

  static void reset() {
    spanProcessor.forceFlushCalled = false;
  }

  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addTracerProviderCustomizer(
        (tracerProvider, config) -> tracerProvider.addSpanProcessor(spanProcessor));

    autoConfigurationCustomizer.addMeterProviderCustomizer(
        (meterProvider, config) ->
            meterProvider.registerMetricReader(StartableMetricReader.INSTANCE));
  }

  @Override
  public void afterAgent(
      Config config, AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    StartableMetricReader.INSTANCE.start();
  }

  @SuppressWarnings("ImmutableEnumChecker")
  private enum StartableMetricReader implements MetricReaderFactory, MetricReader {
    INSTANCE;

    @Nullable private volatile MetricProducer metricProducer;

    private volatile MetricReader delegate = NoopMetricReader.INSTANCE;

    void start() {
      MetricProducer metricProducer = this.metricProducer;
      requireNonNull(metricProducer);
      delegate =
          PeriodicMetricReader.builder(AgentTestingExporterFactory.metricExporter)
              .setInterval(Duration.ofMillis(300))
              .newMetricReaderFactory()
              .apply(metricProducer);
    }

    @Nullable
    @Override
    public AggregationTemporality getPreferredTemporality() {
      return delegate.getPreferredTemporality();
    }

    @Override
    public CompletableResultCode flush() {
      return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
      return delegate.shutdown();
    }

    @Override
    public MetricReader apply(MetricProducer metricProducer) {
      this.metricProducer = metricProducer;
      return this;
    }
  }

  private enum NoopMetricReader implements MetricReader {
    INSTANCE;

    @Nullable
    @Override
    public AggregationTemporality getPreferredTemporality() {
      return null;
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      return CompletableResultCode.ofSuccess();
    }
  }
}
