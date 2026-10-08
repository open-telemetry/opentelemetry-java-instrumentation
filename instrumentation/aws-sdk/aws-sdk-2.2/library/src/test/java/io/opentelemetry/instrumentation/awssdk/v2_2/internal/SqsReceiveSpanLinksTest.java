/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

class SqsReceiveSpanLinksTest {

  @ParameterizedTest
  @MethodSource("spanContexts")
  void delegatesMessageLink(SpanContext spanContext) {
    SpanBuilder spanBuilder = mock(SpanBuilder.class, RETURNS_SELF);
    when(spanBuilder.startSpan()).thenReturn(Span.getInvalid());
    Tracer tracer = mock(Tracer.class);
    when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
    TracerBuilder tracerBuilder = mock(TracerBuilder.class, RETURNS_SELF);
    when(tracerBuilder.build()).thenReturn(tracer);
    TracerProvider tracerProvider = mock(TracerProvider.class);
    when(tracerProvider.tracerBuilder(anyString())).thenReturn(tracerBuilder);
    OpenTelemetry openTelemetry = mock(OpenTelemetry.class);
    when(openTelemetry.getTracerProvider()).thenReturn(tracerProvider);
    when(openTelemetry.getMeterProvider()).thenReturn(OpenTelemetry.noop().getMeterProvider());

    SqsMessage message = mock(SqsMessage.class);
    when(message.getCreationContext()).thenReturn(Context.root().with(Span.wrap(spanContext)));
    when(message.getMessageId()).thenReturn("message-1");
    ExecutionAttributes sdkRequest = new ExecutionAttributes();
    sdkRequest.putAttribute(
        TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE,
        ReceiveMessageRequest.builder().queueUrl("https://example.com/queue").build());
    SqsReceiveRequest request = SqsReceiveRequest.create(sdkRequest, singletonList(message));
    Instrumenter<SqsReceiveRequest, Response> instrumenter =
        new AwsSdkInstrumenterFactory(
                openTelemetry, null, IncludeExclude.builder().build(), false, true, false)
            .consumerReceiveInstrumenter();

    Context context = instrumenter.start(Context.root(), request);
    instrumenter.end(context, request, null, null);

    verify(spanBuilder).addLink(spanContext, Attributes.of(MESSAGING_MESSAGE_ID, "message-1"));
  }

  private static Stream<SpanContext> spanContexts() {
    return Stream.of(
        SpanContext.getInvalid(),
        SpanContext.createFromRemoteParent(
            "00000000000000000000000000000001",
            "0000000000000002",
            TraceFlags.getSampled(),
            TraceState.getDefault()),
        SpanContext.create(
            "00000000000000000000000000000000",
            "0000000000000000",
            TraceFlags.getDefault(),
            TraceState.builder().put("vendor", "value").build()));
  }
}
