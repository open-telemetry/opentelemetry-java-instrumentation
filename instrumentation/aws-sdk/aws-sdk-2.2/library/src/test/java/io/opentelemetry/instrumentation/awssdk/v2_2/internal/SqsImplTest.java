/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

class SqsImplTest {

  private static final String CUSTOM_TRACEPARENT =
      "00-11111111111111111111111111111111-1111111111111111-01";
  private static final String SEND_TRACE_ID = "22222222222222222222222222222222";
  private static final String SEND_SPAN_ID = "2222222222222222";

  @Test
  void injectsSendContextIntoContextFreeStableBatchEntries() {
    assumeTrue(emitStableMessagingSemconv());
    SendMessageBatchRequest request =
        SendMessageBatchRequest.builder()
            .queueUrl("https://sqs.us-east-1.amazonaws.com/123456789012/test")
            .entries(
                SendMessageBatchRequestEntry.builder()
                    .id("custom")
                    .messageBody("custom")
                    .messageAttributes(
                        singletonMap(
                            "traceparent",
                            MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(CUSTOM_TRACEPARENT)
                                .build()))
                    .build(),
                SendMessageBatchRequestEntry.builder()
                    .id("context-free")
                    .messageBody("context-free")
                    .build())
            .build();
    SpanContext sendSpanContext =
        SpanContext.create(
            SEND_TRACE_ID, SEND_SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    Context sendContext = Context.root().with(Span.wrap(sendSpanContext));

    SendMessageBatchRequest modifiedRequest =
        (SendMessageBatchRequest)
            SqsImpl.modifyRequest(
                request, sendContext, false, W3CTraceContextPropagator.getInstance());

    assertThat(
            modifiedRequest.entries().get(0).messageAttributes().get("traceparent").stringValue())
        .isEqualTo(CUSTOM_TRACEPARENT);
    assertThat(
            modifiedRequest.entries().get(1).messageAttributes().get("traceparent").stringValue())
        .isEqualTo("00-" + SEND_TRACE_ID + "-" + SEND_SPAN_ID + "-01");
  }
}
