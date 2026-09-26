/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.PROCESS_DURATION;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryState;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class JmsInstrumenterFactoryTest {

  private static final String INSTRUMENTATION_NAME = "test-jms";

  @Test
  void recordsIndependentProcessDurationUnderOptedInProcessParent() {
    assertThat(emitStableMessagingSemconv()).isTrue();

    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
    try (OpenTelemetrySdk openTelemetry =
        OpenTelemetrySdk.builder()
            .setMeterProvider(meterProvider)
            .setTracerProvider(tracerProvider)
            .build()) {
      Instrumenter<MessageWithDestination, Void> instrumenter =
          new JmsInstrumenterFactory(openTelemetry, INSTRUMENTATION_NAME)
              .createConsumerProcessInstrumenter(false);
      MessageWithDestination request = MessageWithDestination.create(messageAdapter(), null);
      Span parent = openTelemetry.getTracer("test").spanBuilder("parent").startSpan();
      Context parentContext =
          MessagingTelemetryState.add(
              MessagingTelemetryState.enable(Context.root().with(parent)),
              PROCESS,
              PROCESS_DURATION);
      Context eligibilityContext = Context.root().with(parent);

      assertThat(instrumenter.shouldStart(eligibilityContext, request)).isTrue();
      Context context = instrumenter.start(parentContext, request);
      instrumenter.end(context, request, null, null);
      parent.end();

      assertThat(spanExporter.getFinishedSpanItems())
          .filteredOn(
              span -> span.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME))
          .singleElement()
          .satisfies(
              span ->
                  assertThat(span.getParentSpanId())
                      .isEqualTo(parent.getSpanContext().getSpanId()));
      Collection<MetricData> metrics = metricReader.collectAllMetrics();
      assertDurationCount(metrics, 1);
    }
  }

  private static MessageAdapter messageAdapter() {
    return mock(MessageAdapter.class);
  }

  private static void assertDurationCount(Collection<MetricData> metrics, long expectedCount) {
    assertThat(metrics)
        .filteredOn(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME)
                    && metric.getName().equals("messaging.process.duration"))
        .singleElement()
        .satisfies(
            metric ->
                assertThat(metric.getHistogramData().getPoints())
                    .singleElement()
                    .satisfies(point -> assertThat(point.getCount()).isEqualTo(expectedCount)));
  }
}
