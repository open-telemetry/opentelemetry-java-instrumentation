/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CLIENT_OPERATION_DURATION;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CONSUMED_MESSAGES;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarSingletons.currentReceiveSpanSuppression;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetrySuppression;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.VirtualFieldStore;
import io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0.SpringPulsarSingletons;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

class ReceiveFailureFallbackTest {
  private static InMemoryMetricReader metricReader;
  private static OpenTelemetrySdk openTelemetry;

  @BeforeAll
  static void setUp() {
    metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    openTelemetry = OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();
    GlobalOpenTelemetry.set(openTelemetry);
  }

  @AfterAll
  static void tearDown() {
    openTelemetry.close();
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_17)
  void springPulsarCountsMessageAfterFailedReceive() {
    Message<?> message = mock(Message.class);
    when(message.getTopicName()).thenReturn("test-topic");
    when(message.getProperties()).thenReturn(emptyMap());
    Consumer<?> consumer = mock(Consumer.class);
    when(consumer.getSubscription()).thenReturn("test-subscription");

    PulsarSingletons.startAndEndConsumerReceive(
        Context.root(),
        message,
        Timer.start(),
        consumer,
        new IllegalStateException("receive failed"));

    assertThat(VirtualFieldStore.wereConsumedMessagesRecorded(message)).isFalse();
    if (!emitStableMessagingSemconv()) {
      return;
    }

    Instrumenter<Message<?>, Void> springInstrumenter =
        SpringPulsarSingletons.instrumenter(
            VirtualFieldStore.wereConsumedMessagesRecorded(message));
    Context parent = VirtualFieldStore.extract(message);
    assertThat(springInstrumenter.shouldStart(parent, message)).isTrue();
    Context processContext = springInstrumenter.start(parent, message);
    springInstrumenter.end(processContext, message, null, null);

    assertThat(metricReader.collectAllMetrics())
        .filteredOn(metric -> metric.getName().equals("messaging.client.consumed.messages"))
        .singleElement()
        .satisfies(
            metric -> {
              assertThat(metric.getInstrumentationScopeInfo().getName())
                  .isEqualTo("io.opentelemetry.spring-pulsar-1.0");
              assertThat(metric.getLongSumData().getPoints())
                  .singleElement()
                  .satisfies(point -> assertThat(point.getValue()).isEqualTo(1));
            });
  }

  @Test
  void pulsarReceiveSuppressionRestoresAfterException() {
    MessagingTelemetrySuppression suppression = currentReceiveSpanSuppression();
    MessagingTelemetrySignals previous = suppression.suppress(RECEIVE, SPAN);

    assertThatIllegalStateException()
        .isThrownBy(
            () -> {
              try {
                assertThat(suppression.isSuppressed(RECEIVE, CLIENT_OPERATION_DURATION)).isFalse();
                assertThat(suppression.isSuppressed(RECEIVE, CONSUMED_MESSAGES)).isFalse();
                throw new IllegalStateException("boom");
              } finally {
                suppression.restore(previous);
              }
            });

    assertThat(suppression.isSuppressed(RECEIVE, SPAN)).isFalse();
  }
}
