/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaConsumerContextUtilTest {

  @RegisterExtension static final OpenTelemetryExtension testing = OpenTelemetryExtension.create();

  @Test
  void restoresProcessParentWithoutSuppressionMarker() {
    assumeTrue(emitStableMessagingSemconv());
    Span parentSpan =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();
    Span processSpan =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("process").startSpan();
    try {
      Context parentContext = Context.root().with(parentSpan);
      Context processContext =
          KafkaConsumerContextUtil.withProcessParentSpan(
              parentContext.with(processSpan), parentContext);

      Context restored = KafkaConsumerContextUtil.withoutLeakedProcessSpan(processContext);

      assertThat(Span.fromContext(restored)).isSameAs(parentSpan);
      assertThat(Span.fromContext(restored).isRecording()).isTrue();
    } finally {
      processSpan.end();
      parentSpan.end();
    }
  }

  @Test
  void leavesUnrelatedCurrentSpanUntouched() {
    assumeTrue(emitStableMessagingSemconv());
    Span parentSpan =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();
    Span processSpan =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("process").startSpan();
    Span unrelatedSpan =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("unrelated").startSpan();
    try {
      Context parentContext = Context.root().with(parentSpan);
      Context processContext =
          KafkaConsumerContextUtil.withProcessParentSpan(
              parentContext.with(processSpan), parentContext);
      Context unrelatedContext = processContext.with(unrelatedSpan);

      assertThat(KafkaConsumerContextUtil.withoutLeakedProcessSpan(unrelatedContext))
          .isSameAs(unrelatedContext);
    } finally {
      unrelatedSpan.end();
      processSpan.end();
      parentSpan.end();
    }
  }
}
