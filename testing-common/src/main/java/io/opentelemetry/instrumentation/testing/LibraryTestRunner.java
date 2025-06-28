/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static java.util.Arrays.asList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.incubator.config.GlobalConfigProvider;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.contrib.baggage.processor.BaggageSpanProcessor;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.testing.internal.MetaDataCollector;
import io.opentelemetry.instrumentation.testing.provider.TestLogRecordExporterComponentProvider;
import io.opentelemetry.instrumentation.testing.provider.TestMetricExporterComponentProvider;
import io.opentelemetry.instrumentation.testing.provider.TestSpanExporterComponentProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of {@link InstrumentationTestRunner} that initializes OpenTelemetry SDK and
 * uses in-memory exporter to collect traces and metrics.
 */
public final class LibraryTestRunner extends InstrumentationTestRunner {

  private static final OpenTelemetrySdk openTelemetrySdk;
  private static final OpenTelemetry openTelemetry;
  private static final InMemorySpanExporter testSpanExporter;
  private static final InMemoryMetricExporter testMetricExporter;
  private static final InMemoryLogRecordExporter testLogRecordExporter;
  private static final MetricReader metricReader;
  private static boolean forceFlushCalled;

  static {
    GlobalOpenTelemetry.resetForTest();
    GlobalConfigProvider.resetForTest();

    testSpanExporter = InMemorySpanExporter.create();
    testMetricExporter = InMemoryMetricExporter.create(AggregationTemporality.DELTA);
    testLogRecordExporter = InMemoryLogRecordExporter.create();
    TestSpanExporterComponentProvider.setSpanExporter(testSpanExporter);
    TestMetricExporterComponentProvider.setMetricExporter(testMetricExporter);
    TestLogRecordExporterComponentProvider.setLogRecordExporter(testLogRecordExporter);

    metricReader =
        PeriodicMetricReader.builder(testMetricExporter)
            // Set really long interval. We'll call forceFlush when we need the metrics
            // instead of collecting them periodically.
            .setInterval(Duration.ofNanos(Long.MAX_VALUE))
            .build();

    openTelemetrySdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(new FlushTrackingSpanProcessor())
                    .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
                    .addSpanProcessor(SimpleSpanProcessor.create(testSpanExporter))
                    .addSpanProcessor(
                        new BaggageSpanProcessor(
                            baggageKey ->
                                asList("test-baggage-key-1", "test-baggage-key-2")
                                    .contains(baggageKey)))
                    .build())
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
            .setLoggerProvider(
                SdkLoggerProvider.builder()
                    .addLogRecordProcessor(SimpleLogRecordProcessor.create(testLogRecordExporter))
                    .build())
            .setPropagators(
                ContextPropagators.create(
                    TextMapPropagator.composite(
                        W3CTraceContextPropagator.getInstance(),
                        W3CBaggagePropagator.getInstance())))
            .buildAndRegisterGlobal();
    openTelemetry = wrap(openTelemetrySdk);
  }

  private static final LibraryTestRunner INSTANCE = new LibraryTestRunner();

  public static LibraryTestRunner instance() {
    return INSTANCE;
  }

  private LibraryTestRunner() {
    super(openTelemetry);
  }

  @Override
  public void beforeTestClass() {
    // just in case: if there was any test that modified the global instance, reset it
    GlobalOpenTelemetry.resetForTest();
    GlobalConfigProvider.resetForTest();
    GlobalOpenTelemetry.set(openTelemetrySdk);
  }

  @Override
  public void afterTestClass() throws IOException {
    // Generates files in a `.telemetry` directory within the instrumentation module with all
    // captured emitted metadata to be used by the instrumentation-docs Doc generator.
    if (Boolean.getBoolean("collectMetadata")) {
      URL resource = this.getClass().getClassLoader().getResource("");
      if (resource == null) {
        return;
      }
      String path = Paths.get(resource.getPath()).toString();

      MetaDataCollector.writeTelemetryToFiles(path, metricsByScope, tracesByScope);
    }
  }

  @Override
  public void clearAllExportedData() {
    // Flush meter provider to remove any lingering measurements
    openTelemetrySdk.getSdkMeterProvider().forceFlush().join(10, TimeUnit.SECONDS);
    testSpanExporter.reset();
    testMetricExporter.reset();
    testLogRecordExporter.reset();
    forceFlushCalled = false;
  }

  @Override
  public OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  public OpenTelemetrySdk getOpenTelemetrySdk() {
    return openTelemetrySdk;
  }

  @Override
  public List<SpanData> getExportedSpans() {
    return testSpanExporter.getFinishedSpanItems();
  }

  @Override
  public List<MetricData> getExportedMetrics() {
    metricReader.forceFlush().join(10, TimeUnit.SECONDS);
    return testMetricExporter.getFinishedMetricItems();
  }

  @Override
  public List<LogRecordData> getExportedLogRecords() {
    return testLogRecordExporter.getFinishedLogRecordItems();
  }

  @Override
  public boolean forceFlushCalled() {
    return forceFlushCalled;
  }

  // Wrap the OpenTelemetry instance similarly to what GlobalOpenTelemetry does. We do this to
  // ensure that the OpenTelemetry instance can not be accidentally closed. This could happen for
  // example when it is registered as spring bean and spring calls close on it, because
  // OpenTelemetrySdk implements Closeable, when the application context is closed.
  private static OpenTelemetry wrap(OpenTelemetry delegate) {
    return new OpenTelemetry() {
      @Override
      public TracerProvider getTracerProvider() {
        return delegate.getTracerProvider();
      }

      @Override
      public MeterProvider getMeterProvider() {
        return delegate.getMeterProvider();
      }

      @Override
      public LoggerProvider getLogsBridge() {
        return delegate.getLogsBridge();
      }

      @Override
      public ContextPropagators getPropagators() {
        return delegate.getPropagators();
      }

      @Override
      public TracerBuilder tracerBuilder(String instrumentationScopeName) {
        return delegate.tracerBuilder(instrumentationScopeName);
      }
    };
  }

  private static class FlushTrackingSpanProcessor implements SpanProcessor {
    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {}

    @Override
    public boolean isStartRequired() {
      return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {}

    @Override
    public boolean isEndRequired() {
      return false;
    }

    @Override
    public CompletableResultCode forceFlush() {
      forceFlushCalled = true;
      return CompletableResultCode.ofSuccess();
    }
  }
}
