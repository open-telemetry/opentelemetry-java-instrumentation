/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.ClientContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InstrumenterExtractionTest {
  private static final String AWS_TRACE_HEADER_PROP = "com.amazonaws.xray.traceHeader";

  @Test
  void useCustomContext() {
    AwsLambdaFunctionInstrumenter instr =
        AwsLambdaFunctionInstrumenterFactory.createInstrumenter(
            OpenTelemetry.propagating(
                ContextPropagators.create(W3CTraceContextPropagator.getInstance())));
    com.amazonaws.services.lambda.runtime.Context awsContext =
        mock(com.amazonaws.services.lambda.runtime.Context.class);
    ClientContext clientContext = mock(ClientContext.class);
    when(awsContext.getClientContext()).thenReturn(clientContext);
    HashMap<String, String> customMap = new HashMap<>();
    customMap.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    when(clientContext.getCustom()).thenReturn(customMap);

    AwsLambdaRequest input = AwsLambdaRequest.create(awsContext, new HashMap<>(), new HashMap<>());

    Context extracted = instr.extract(input);
    SpanContext spanContext = Span.fromContext(extracted).getSpanContext();
    assertThat(spanContext.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    assertThat(spanContext.getSpanId()).isEqualTo("00f067aa0ba902b7");
  }

  @Test
  void useXrayTraceIdFromAwsContext() {
    assumeTrue(hasXrayTraceIdApi(), "requires aws-lambda-java-core with getXrayTraceId()");

    String traceHeader =
        "Root=1-00000001-d188f8fa79d48a391a778fa6;Parent=53995c3f42cd8ad8;Sampled=1";
    AtomicReference<String> extractedTraceHeader = new AtomicReference<>();
    AwsLambdaFunctionInstrumenter instr =
        AwsLambdaFunctionInstrumenterFactory.createInstrumenter(
            OpenTelemetry.propagating(
                ContextPropagators.create(new TraceHeaderPropagator(extractedTraceHeader))));

    ContextWithXrayTraceId contextWithXrayTraceId = mock(ContextWithXrayTraceId.class);
    when(contextWithXrayTraceId.getXrayTraceId()).thenReturn(traceHeader);
    AwsLambdaRequest input =
        AwsLambdaRequest.create(contextWithXrayTraceId, new Object(), emptyMap());

    instr.extract(input);

    assertThat(extractedTraceHeader.get()).isEqualTo(traceHeader);
  }

  private static boolean hasXrayTraceIdApi() {
    try {
      com.amazonaws.services.lambda.runtime.Context.class.getMethod("getXrayTraceId");
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static final class TraceHeaderPropagator implements TextMapPropagator {
    private final AtomicReference<String> extractedTraceHeader;

    private TraceHeaderPropagator(AtomicReference<String> extractedTraceHeader) {
      this.extractedTraceHeader = extractedTraceHeader;
    }

    @Override
    public List<String> fields() {
      return singletonList(AWS_TRACE_HEADER_PROP);
    }

    @Override
    public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {}

    @Override
    public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
      extractedTraceHeader.set(getter.get(carrier, AWS_TRACE_HEADER_PROP));
      return context;
    }
  }

  // this class exposes getXrayTraceId method that is not present in the earliest tested version
  private interface ContextWithXrayTraceId extends com.amazonaws.services.lambda.runtime.Context {
    // Context has this method only in latest dep tests
    @SuppressWarnings("MissingOverride")
    String getXrayTraceId();
  }
}
