/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.ClientContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class InstrumenterExtractionTest {
  @Test
  public void useCustomContext() {
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
}
