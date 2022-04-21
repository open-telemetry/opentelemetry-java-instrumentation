/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.server.ServerSpan;
import org.junit.jupiter.api.Test;

class LegacyServerSpanContextBridgeTest {

  // cannot use AgentInstrumentationExtension because it'd try to initialize Instrumenters with new
  // SpanKeys
  static final Tracer tracer = GlobalOpenTelemetry.get().getTracer("test");

  @Test
  void shouldBridgeLegacyServerSpanClass() {
    AgentSpanTesting.runWithServerSpan(
        "server",
        () -> {
          assertNotNull(Span.current());
          assertNotNull(ServerSpan.fromContextOrNull(Context.current()));

          Span internalSpan = tracer.spanBuilder("internal").startSpan();
          try (Scope ignored = internalSpan.makeCurrent()) {
            assertNotNull(ServerSpan.fromContextOrNull(Context.current()));
          } finally {
            internalSpan.end();
          }
        });
  }
}
