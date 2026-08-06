/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.azurefunctions.worker.v2_0;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.microsoft.azure.functions.rpc.messages.TestInvocationRequest;
import com.microsoft.azure.functions.rpc.messages.TestRpcTraceContext;
import com.microsoft.azure.functions.worker.handler.InvocationRequestHandler;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AzureFunctionsWorkerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final String TRACE_ID = "00000000000000000000000000000123";
  // the parent span here belongs to the Azure Functions host and is never exported, so the trace
  // has no root span. TelemetryDataUtil.isCompleted() special cases this span id, without it
  // waitAndAssertTraces would time out waiting for a root span that never arrives.
  private static final String SPAN_ID = "0000000000000456";

  @Test
  void adoptsSampledTraceContext() {
    SpanContext spanContext = invoke("00-" + TRACE_ID + "-" + SPAN_ID + "-01", "foo=bar");

    assertThat(spanContext.getTraceId()).isEqualTo(TRACE_ID);
    assertThat(spanContext.getSpanId()).isEqualTo(SPAN_ID);
    assertThat(spanContext.isSampled()).isTrue();
    assertThat(spanContext.isRemote()).isTrue();
    assertThat(spanContext.getTraceState().get("foo")).isEqualTo("bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("function")
                        .hasTraceId(TRACE_ID)
                        .hasParentSpanId(SPAN_ID)
                        .hasTotalAttributeCount(0)));
  }

  // the host forwards the sampling decision of the caller, overriding it here would resurrect
  // traces that the caller decided not to sample
  @Test
  void keepsNotSampledTraceContext() {
    SpanContext spanContext = invoke("00-" + TRACE_ID + "-" + SPAN_ID + "-00", "");

    assertThat(spanContext.getTraceId()).isEqualTo(TRACE_ID);
    assertThat(spanContext.getSpanId()).isEqualTo(SPAN_ID);
    assertThat(spanContext.isSampled()).isFalse();
    assertThat(spanContext.isRemote()).isTrue();
  }

  // the host sends an empty traceparent when it has no trace context to pass on
  @Test
  void ignoresEmptyTraceParent() {
    assertThat(invoke("", "").isValid()).isFalse();
  }

  @Test
  void ignoresMalformedTraceParent() {
    assertThat(invoke("not-a-traceparent", "").isValid()).isFalse();
  }

  @Test
  void ignoresMissingTraceContext() {
    InvocationRequestHandler handler = new InvocationRequestHandler();
    handler.execute(new TestInvocationRequest(null), null);

    assertThat(handler.getCapturedSpanContext().isValid()).isFalse();
  }

  private static SpanContext invoke(String traceParent, String traceState) {
    InvocationRequestHandler handler = new InvocationRequestHandler();
    InvocationRequest request =
        new TestInvocationRequest(new TestRpcTraceContext(traceParent, traceState));
    handler.execute(request, null);
    return handler.getCapturedSpanContext();
  }
}
