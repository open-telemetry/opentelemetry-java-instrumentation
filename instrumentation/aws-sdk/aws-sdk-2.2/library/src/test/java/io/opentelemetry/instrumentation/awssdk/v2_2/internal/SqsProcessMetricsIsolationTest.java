/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.PROCESS_DURATION;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryState.contains;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryState.enable;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata;
import software.amazon.awssdk.core.ClientType;
import software.amazon.awssdk.core.interceptor.Context.AfterExecution;
import software.amazon.awssdk.core.interceptor.Context.BeforeTransmission;
import software.amazon.awssdk.core.interceptor.Context.ModifyRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

class SqsProcessMetricsIsolationTest {

  private static final String AWS_INSTRUMENTATION_NAME = "io.opentelemetry.aws-sdk-2.2";
  private static final String OUTER_INSTRUMENTATION_NAME = "test-camel";
  private static final String QUEUE_URL = "http://localhost:9324/queue/testSdkSqs";

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void processDurationIsolatedFromOuterProcessWhenReceiveTelemetryDisabled() {
    assertProcessDurationIsolated(false);
  }

  @Test
  void processDurationIsolatedFromOuterProcessWhenReceiveTelemetryEnabled() {
    assertProcessDurationIsolated(true);
  }

  private static void assertProcessDurationIsolated(boolean receiveTelemetryEnabled) {
    assumeTrue(emitStableMessagingSemconv());

    Instrumenter<String, Void> outerInstrumenter = newOuterProcessInstrumenter();
    Context callerContext = Context.current();
    assertThat(outerInstrumenter.shouldStart(callerContext, "outer")).isTrue();
    Context outerContext = outerInstrumenter.start(callerContext, "outer");
    SpanContext outerSpanContext = Span.fromContext(outerContext).getSpanContext();
    assertThat(contains(outerContext, PROCESS, PROCESS_DURATION)).isTrue();

    AtomicReference<SpanContext> processSpanContext = new AtomicReference<>();
    try (Scope ignored = outerContext.makeCurrent()) {
      ReceiveMessageResponse response = receiveMessage(receiveTelemetryEnabled);

      response
          .messages()
          .forEach(
              message -> {
                assertThat(contains(Context.current(), PROCESS, PROCESS_DURATION)).isTrue();
                processSpanContext.set(Span.current().getSpanContext());
              });

      assertThat(processSpanContext.get()).isNotNull();
      assertThat(Context.current()).isSameAs(outerContext);
    } finally {
      outerInstrumenter.end(outerContext, "outer", null, null);
    }
    assertThat(Context.current()).isSameAs(callerContext);

    testing.waitForTraces(1);
    assertThat(testing.spans())
        .filteredOn(span -> span.getSpanId().equals(processSpanContext.get().getSpanId()))
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getTraceId()).isEqualTo(outerSpanContext.getTraceId());
              assertThat(span.getParentSpanId()).isEqualTo(outerSpanContext.getSpanId());
            });

    testing.waitAndAssertMetrics(
        AWS_INSTRUMENTATION_NAME,
        "messaging.client.consumed.messages",
        metrics ->
            metrics
                .singleElement()
                .satisfies(
                    metric ->
                        assertThat(metric.getLongSumData().getPoints())
                            .singleElement()
                            .satisfies(point -> assertThat(point.getValue()).isEqualTo(1))));
    testing.waitAndAssertMetrics(
        AWS_INSTRUMENTATION_NAME,
        "messaging.process.duration",
        metrics ->
            metrics
                .singleElement()
                .satisfies(
                    metric ->
                        assertThat(metric.getHistogramData().getPoints())
                            .singleElement()
                            .satisfies(point -> assertThat(point.getCount()).isEqualTo(1))));
    testing.waitAndAssertMetrics(
        OUTER_INSTRUMENTATION_NAME,
        "messaging.process.duration",
        metrics ->
            metrics
                .singleElement()
                .satisfies(
                    metric ->
                        assertThat(metric.getHistogramData().getPoints())
                            .singleElement()
                            .satisfies(point -> assertThat(point.getCount()).isEqualTo(1))));
  }

  private static Instrumenter<String, Void> newOuterProcessInstrumenter() {
    return Instrumenter.<String, Void>builder(
            testing.getOpenTelemetry(), OUTER_INSTRUMENTATION_NAME, request -> "camel process")
        .addContextCustomizer((context, request, startAttributes) -> enable(context))
        .addOperationMetrics(MessagingProcessMetrics.get())
        .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static ReceiveMessageResponse receiveMessage(boolean receiveTelemetryEnabled) {
    AwsSdkTelemetry telemetry =
        AwsSdkTelemetry.builder(testing.getOpenTelemetry())
            .setMessagingReceiveTelemetryEnabled(receiveTelemetryEnabled)
            .build();
    ExecutionInterceptor interceptor = telemetry.createExecutionInterceptor();
    SqsClient sdkClient = mock(SqsClient.class);
    when(sdkClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenAnswer(
            invocation ->
                executeReceive(
                    interceptor, invocation.getArgument(0, ReceiveMessageRequest.class)));

    SqsClient client = telemetry.wrap(sdkClient);
    return client.receiveMessage(ReceiveMessageRequest.builder().queueUrl(QUEUE_URL).build());
  }

  private static ReceiveMessageResponse executeReceive(
      ExecutionInterceptor interceptor, ReceiveMessageRequest request) {
    ExecutionAttributes executionAttributes = new ExecutionAttributes();
    executionAttributes.putAttribute(SdkExecutionAttribute.CLIENT_TYPE, ClientType.SYNC);
    executionAttributes.putAttribute(SdkExecutionAttribute.SERVICE_NAME, "Sqs");
    executionAttributes.putAttribute(SdkExecutionAttribute.OPERATION_NAME, "ReceiveMessage");

    ModifyRequest modifyRequest = mock(ModifyRequest.class);
    when(modifyRequest.request()).thenReturn(request);
    interceptor.modifyRequest(modifyRequest, executionAttributes);

    SdkHttpRequest httpRequest =
        SdkHttpRequest.builder().uri(URI.create(QUEUE_URL)).method(SdkHttpMethod.POST).build();
    BeforeTransmission beforeTransmission = mock(BeforeTransmission.class);
    when(beforeTransmission.httpRequest()).thenReturn(httpRequest);
    interceptor.beforeTransmission(beforeTransmission, executionAttributes);

    ReceiveMessageResponse.Builder responseBuilder = ReceiveMessageResponse.builder();
    responseBuilder.messages(
        Message.builder()
            .messageId("message-id")
            .receiptHandle("receipt-handle")
            .body("test")
            .build());
    responseBuilder.responseMetadata(DefaultAwsResponseMetadata.create(emptyMap()));
    ReceiveMessageResponse response = responseBuilder.build();
    SdkHttpResponse httpResponse = SdkHttpResponse.builder().statusCode(200).build();
    AfterExecution afterExecution = mock(AfterExecution.class);
    when(afterExecution.request()).thenReturn(request);
    when(afterExecution.response()).thenReturn(response);
    when(afterExecution.httpRequest()).thenReturn(httpRequest);
    when(afterExecution.httpResponse()).thenReturn(httpResponse);
    interceptor.afterExecution(afterExecution, executionAttributes);
    return response;
  }
}
