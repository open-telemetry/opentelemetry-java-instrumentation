/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

class Aws2SqsW3cPropagatorAndXrayPropagatorTest extends Aws2SqsTracingTest {
  private static final String CUSTOM_TRACEPARENT =
      "00-11111111111111111111111111111111-1111111111111111-01";
  private static final String CUSTOM_XRAY_CONTEXT =
      "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1";
  private static final String INVALID_XRAY_CONTEXT = "invalid";

  @Override
  void configure(AwsSdkTelemetryBuilder telemetryBuilder) {
    telemetryBuilder.setUseConfiguredPropagatorForMessaging(
        isSqsAttributeInjectionEnabled()); // Difference to main test
  }

  @Override
  protected boolean isSqsAttributeInjectionEnabled() {
    return true;
  }

  @Test
  void testPreservesCustomBatchCreationContexts() {
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
                    .messageBody("e1")
                    .messageAttributes(
                        singletonMap(
                            "traceparent",
                            MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(CUSTOM_TRACEPARENT)
                                .build()))
                    .build(),
                SendMessageBatchRequestEntry.builder()
                    .id("i2")
                    .messageBody("e2")
                    .messageAttributes(
                        singletonMap(
                            "X-Amzn-Trace-Id",
                            MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(CUSTOM_XRAY_CONTEXT)
                                .build()))
                    .build(),
                SendMessageBatchRequestEntry.builder().id("i3").messageBody("e3").build())
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
                                        .extracting(link -> link.getSpanContext().getSpanId())
                                        .containsExactlyInAnyOrder(
                                            "1111111111111111",
                                            "53995c3f42cd8ad8",
                                            createSpan.get().getSpanId()))));

    ReceiveMessageResponse response = client.receiveMessage(receiveMessageBatchRequest);
    assertThat(response.messages())
        .extracting(
            message -> {
              MessageAttributeValue value = message.messageAttributes().get("traceparent");
              return value == null ? null : value.stringValue();
            })
        .contains(CUSTOM_TRACEPARENT);
    assertThat(response.messages())
        .extracting(
            message -> {
              MessageAttributeValue value = message.messageAttributes().get("X-Amzn-Trace-Id");
              return value == null ? null : value.stringValue();
            })
        .contains(CUSTOM_XRAY_CONTEXT);
  }

  @Test
  void testCreatesContextsWhenOnlyOnePropagatorIsOccupied() {
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
                    .messageBody("baggage-only")
                    .messageAttributes(
                        singletonMap(
                            "baggage",
                            MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue("user=alice")
                                .build()))
                    .build(),
                SendMessageBatchRequestEntry.builder()
                    .id("i2")
                    .messageBody("invalid-xray")
                    .messageAttributes(
                        singletonMap(
                            "X-Amzn-Trace-Id",
                            MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(INVALID_XRAY_CONTEXT)
                                .build()))
                    .build())
            .build();

    client.sendMessageBatch(batchRequest);

    List<SpanData> createSpans = new ArrayList<>();
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("Sqs.CreateQueue").hasKind(SpanKind.CLIENT)),
            trace -> {
              createSpans.add(trace.getSpan(0));
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("create testSdkSqs").hasKind(SpanKind.PRODUCER));
            },
            trace -> {
              createSpans.add(trace.getSpan(0));
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
                                        .extracting(link -> link.getSpanContext().getSpanId())
                                        .containsExactlyInAnyOrder(
                                            createSpans.get(0).getSpanId(),
                                            createSpans.get(1).getSpanId()))));

    ReceiveMessageResponse response = client.receiveMessage(receiveMessageBatchRequest);
    assertThat(response.messages())
        .filteredOn(message -> "baggage-only".equals(message.body()))
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.messageAttributes().get("baggage").stringValue())
                  .isEqualTo("user=alice");
              assertThat(message.messageAttributes()).containsKey("X-Amzn-Trace-Id");
            });
    assertThat(response.messages())
        .filteredOn(message -> "invalid-xray".equals(message.body()))
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.messageAttributes().get("X-Amzn-Trace-Id").stringValue())
                  .isEqualTo(INVALID_XRAY_CONTEXT);
              assertThat(message.messageAttributes()).containsKey("traceparent");
            });
  }
}
