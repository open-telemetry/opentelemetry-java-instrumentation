/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.server.ServerSpan;
import io.opentelemetry.javaagent.instrumentation.testing.AgentSpanTesting;
import org.junit.jupiter.api.Test;

class LegacyServerSpanContextBridgeTest {

  // cannot use AgentInstrumentationExtension because it'd try to initialize Instrumenters with new
  // SpanKeys
  static final Tracer tracer = GlobalOpenTelemetry.get().getTracer("test");

  @Test
  void shouldBridgeLegacyServerSpanClass() {
    AgentSpanTesting.runWithHttpServerSpan(
        "server",
        () -> {
          assertThat(Span.current()).isNotNull();
          assertThat(ServerSpan.fromContextOrNull(Context.current())).isNotNull();

          Span internalSpan = tracer.spanBuilder("internal").startSpan();
          try (Scope ignored = internalSpan.makeCurrent()) {
            assertThat(ServerSpan.fromContextOrNull(Context.current())).isNotNull();
          } finally {
            internalSpan.end();
          }
        });
  }
}
