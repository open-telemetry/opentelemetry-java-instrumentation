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
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
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

    String xrayTraceId =
        "Root=1-00000001-d188f8fa79d48a391a778fa6;Parent=53995c3f42cd8ad8;Sampled=1";
    AtomicReference<String> extractedTraceHeader = new AtomicReference<>();
    AwsLambdaFunctionInstrumenter instr =
        AwsLambdaFunctionInstrumenterFactory.createInstrumenter(
            OpenTelemetry.propagating(
                ContextPropagators.create(new TraceHeaderPropagator(extractedTraceHeader))));

    AwsLambdaRequest input =
        AwsLambdaRequest.create(new ContextWithXrayTraceId(xrayTraceId), new Object(), emptyMap());

    instr.extract(input);

    assertThat(extractedTraceHeader.get()).isEqualTo(xrayTraceId);
  }

  private static boolean hasXrayTraceIdApi() {
    try {
      com.amazonaws.services.lambda.runtime.Context.class.getMethod("getXrayTraceId");
      return true;
    } catch (NoSuchMethodException | SecurityException ignored) {
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

  private static final class ContextWithXrayTraceId
      implements com.amazonaws.services.lambda.runtime.Context, XrayTraceIdContext {
    private final String xrayTraceId;

    private ContextWithXrayTraceId(String xrayTraceId) {
      this.xrayTraceId = xrayTraceId;
    }

    @Override
    @SuppressWarnings("EffectivelyPrivate")
    public String getXrayTraceId() {
      return xrayTraceId;
    }

    @Override
    public String getAwsRequestId() {
      return null;
    }

    @Override
    public String getLogGroupName() {
      return null;
    }

    @Override
    public String getLogStreamName() {
      return null;
    }

    @Override
    public String getFunctionName() {
      return null;
    }

    @Override
    public String getFunctionVersion() {
      return null;
    }

    @Override
    public String getInvokedFunctionArn() {
      return null;
    }

    @Override
    public CognitoIdentity getIdentity() {
      return null;
    }

    @Override
    public ClientContext getClientContext() {
      return null;
    }

    @Override
    public int getRemainingTimeInMillis() {
      return 0;
    }

    @Override
    public int getMemoryLimitInMB() {
      return 0;
    }

    @Override
    public LambdaLogger getLogger() {
      return null;
    }
  }
}
