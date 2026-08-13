/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MessagingReceiveTelemetryTest {

  @RegisterExtension static final OpenTelemetryExtension testing = OpenTelemetryExtension.create();

  private static final Instrumenter<String, String> instrumenter =
      Instrumenter.<String, String>builder(
              testing.getOpenTelemetry(), "test", request -> "receive " + request)
          .buildInstrumenter(request -> SpanKind.CONSUMER);

  @Test
  void spanEligibleCreatesReceiveSpanAndReturnsItsContext() {
    Timer timer = Timer.start();

    Context receiveContext =
        MessagingReceiveTelemetry.record(
            instrumenter, Context.root(), "queue", null, null, timer, /* spanEligible= */ true);

    assertThat(receiveContext).isNotNull();
    List<SpanData> spans = testing.getSpans();
    assertThat(spans).hasSize(1);
    assertThat(spans.get(0)).hasName("receive queue").hasKind(SpanKind.CONSUMER);
  }

  @Test
  void spanIneligibleRecordsNoSpanAndReturnsNull() {
    Timer timer = Timer.start();

    Context receiveContext =
        MessagingReceiveTelemetry.record(
            instrumenter, Context.root(), "queue", null, null, timer, /* spanEligible= */ false);

    assertThat(receiveContext).isNull();
    assertThat(testing.getSpans()).isEmpty();
  }

  @Test
  void disabledInstrumenterRecordsNothing() {
    Instrumenter<String, String> disabled =
        Instrumenter.<String, String>builder(
                testing.getOpenTelemetry(), "test", request -> "receive " + request)
            .setEnabled(false)
            .buildInstrumenter(request -> SpanKind.CONSUMER);

    assertThat(
            MessagingReceiveTelemetry.record(
                disabled, Context.root(), "queue", null, null, Timer.start(), false))
        .isNull();
    assertThat(
            MessagingReceiveTelemetry.record(
                disabled, Context.root(), "queue", null, null, Timer.start(), true))
        .isNull();
    assertThat(testing.getSpans()).isEmpty();
  }
}
