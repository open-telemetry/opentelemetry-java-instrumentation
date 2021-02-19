/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Collections;
import java.util.List;

/**
 * An implementation of {@link InstrumentationTestRunner} that initializes OpenTelemetry SDK and
 * uses in-memory exporter to collect traces and metrics.
 */
public final class LibraryTestRunner implements InstrumentationTestRunner {

  private static final OpenTelemetrySdk openTelemetry;
  private static final InMemorySpanExporter testExporter;
  private static boolean forceFlushCalled;
  private static final LibraryTestRunner INSTANCE = new LibraryTestRunner();

  static {
    GlobalOpenTelemetry.resetForTest();

    testExporter = InMemorySpanExporter.create();
    openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(new FlushTrackingSpanProcessor())
                    .addSpanProcessor(SimpleSpanProcessor.create(testExporter))
                    .build())
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();
  }

  public static LibraryTestRunner instance() {
    return INSTANCE;
  }

  @Override
  public void beforeTestClass() {
    // just in case: if there was any test that modified the global instance, reset it
    if (GlobalOpenTelemetry.get() != openTelemetry) {
      GlobalOpenTelemetry.resetForTest();
      GlobalOpenTelemetry.set(openTelemetry);
    }
  }

  @Override
  public void afterTestClass() {}

  @Override
  public void clearAllExportedData() {
    testExporter.reset();
    forceFlushCalled = false;
  }

  @Override
  public List<SpanData> getExportedSpans() {
    return testExporter.getFinishedSpanItems();
  }

  @Override
  public List<MetricData> getExportedMetrics() {
    // no metrics support yet
    return Collections.emptyList();
  }

  public OpenTelemetrySdk getOpenTelemetrySdk() {
    return openTelemetry;
  }

  @Override
  public boolean forceFlushCalled() {
    return forceFlushCalled;
  }

  private LibraryTestRunner() {}

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
