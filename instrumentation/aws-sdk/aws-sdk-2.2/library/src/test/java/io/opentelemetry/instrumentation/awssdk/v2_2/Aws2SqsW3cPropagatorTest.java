/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

class Aws2SqsW3cPropagatorTest extends Aws2SqsTracingTest {
  private static final String INVALID_TRACEPARENT = "invalid";

  @Override
  void configure(AwsSdkTelemetryBuilder telemetryBuilder) {
    telemetryBuilder
        .setUseConfiguredPropagatorForMessaging(
            isSqsAttributeInjectionEnabled()) // Difference to main test
        .setUseXrayPropagator(
            isXrayInjectionEnabled()); // Disable to confirm messaging propagator actually works
  }

  @Override
  protected boolean isSqsAttributeInjectionEnabled() {
    return true;
  }

  @Override
  protected boolean isXrayInjectionEnabled() {
    return false;
  }

  @Test
  void testDoesNotCreateContextWhenTraceFieldCannotBeInjected() {
    assumeTrue(emitStableMessagingSemconv());
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());
    client.createQueue(createQueueRequest);
    SendMessageBatchRequest batchRequest =
        SendMessageBatchRequest.builder()
            .queueUrl(queueUrl)
            .entries(
                SendMessageBatchRequestEntry.builder()
                    .id("i1")
                    .messageBody("test")
                    .messageAttributes(
                        singletonMap(
                            "traceparent",
                            MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(INVALID_TRACEPARENT)
                                .build()))
                    .build())
            .build();

    try (Scope ignored = Baggage.builder().put("user", "alice").build().makeCurrent()) {
      client.sendMessageBatch(batchRequest);
    }

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("Sqs.CreateQueue").hasKind(SpanKind.CLIENT)),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("send testSdkSqs")
                            .hasKind(SpanKind.CLIENT)
                            .hasTotalRecordedLinks(0)));
  }

  @Test
  void testInjectsMissingPropagationFieldAtAttributeLimit() {
    assumeTrue(emitStableMessagingSemconv());
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());
    client.createQueue(createQueueRequest);

    Map<String, MessageAttributeValue> messageAttributes = dummyMessageAttributes(8);
    messageAttributes.put(
        "baggage",
        MessageAttributeValue.builder().dataType("String").stringValue("existing=value").build());
    SendMessageBatchRequest batchRequest =
        SendMessageBatchRequest.builder()
            .queueUrl(queueUrl)
            .entries(
                singletonList(
                    SendMessageBatchRequestEntry.builder()
                        .id("i1")
                        .messageBody("test")
                        .messageAttributes(messageAttributes)
                        .build()))
            .build();

    client.sendMessageBatch(batchRequest);

    AtomicReference<SpanData> createSpan = new AtomicReference<>();
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("Sqs.CreateQueue").hasKind(SpanKind.CLIENT)),
            trace -> {
              createSpan.set(trace.getSpan(0));
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("create testSdkSqs").hasKind(SpanKind.PRODUCER));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("send testSdkSqs")
                            .hasKind(SpanKind.CLIENT)
                            .hasLinksSatisfying(
                                links ->
                                    assertThat(links)
                                        .singleElement()
                                        .satisfies(
                                            link ->
                                                assertThat(link.getSpanContext().getSpanId())
                                                    .isEqualTo(createSpan.get().getSpanId())))));

    ReceiveMessageResponse response = client.receiveMessage(receiveMessageBatchRequest);
    assertThat(response.messages()).hasSize(1);
    // iterate the whole list so that the process scope opened for each message is closed
    response
        .messages()
        .forEach(
            message -> {
              assertThat(message.messageAttributes()).hasSize(10).containsKey("traceparent");
              assertThat(message.messageAttributes().get("baggage").stringValue())
                  .isEqualTo("existing=value");
            });
  }
}
