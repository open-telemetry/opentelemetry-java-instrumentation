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
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

class Aws2SqsW3cPropagatorAndXrayPropagatorTest extends Aws2SqsTracingTest {
  private static final String CUSTOM_TRACEPARENT =
      "00-11111111111111111111111111111111-1111111111111111-01";
  private static final String INVALID_TRACEPARENT = "invalid";

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
                SendMessageBatchRequestEntry.builder().id("i2").messageBody("e2").build())
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
                                            "1111111111111111", createSpan.get().getSpanId()))));

    ReceiveMessageResponse response = client.receiveMessage(receiveMessageBatchRequest);
    assertThat(response.messages())
        .extracting(
            message -> {
              MessageAttributeValue value = message.messageAttributes().get("traceparent");
              return value == null ? null : value.stringValue();
            })
        .contains(CUSTOM_TRACEPARENT);
  }

  @Test
  void testDisabledCreateSpansPreserveCustomBatchCreationContexts() {
    assumeTrue(emitStableMessagingSemconv());
    AwsSdkTelemetry disabledTelemetry =
        AwsSdkTelemetry.builder(getTesting().getOpenTelemetry())
            .setCaptureExperimentalSpanAttributes(true)
            .setUseConfiguredPropagatorForMessaging(true)
            .setMessageCreateSpansEnabled(false)
            .build();
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    builder.overrideConfiguration(
        ClientOverrideConfiguration.builder()
            .addExecutionInterceptor(disabledTelemetry.createExecutionInterceptor())
            .build());
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
                SendMessageBatchRequestEntry.builder().id("i2").messageBody("e2").build())
            .build();

    try (SqsClient client = disabledTelemetry.wrap(builder.build())) {
      client.createQueue(createQueueRequest);
      client.sendMessageBatch(batchRequest);

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
                              .hasLinksSatisfying(
                                  links ->
                                      assertThat(links)
                                          .singleElement()
                                          .satisfies(
                                              link ->
                                                  assertThat(link.getSpanContext().getSpanId())
                                                      .isEqualTo("1111111111111111")))));

      ReceiveMessageResponse response = client.receiveMessage(receiveMessageBatchRequest);
      assertThat(response.messages())
          .filteredOn(message -> "e1".equals(message.body()))
          .singleElement()
          .satisfies(
              message ->
                  assertThat(message.messageAttributes().get("traceparent").stringValue())
                      .isEqualTo(CUSTOM_TRACEPARENT));
    }
  }

  @Test
  void testCreatesContextsWhenOnlyOnePropagatorIsOccupied() {
    assumeTrue(emitStableMessagingSemconv());
    assumeTrue(supportsMessageSystemAttributes());
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
                    .messageBody("invalid-traceparent")
                    .messageAttributes(
                        singletonMap(
                            "traceparent",
                            MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(INVALID_TRACEPARENT)
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
              assertThat(message.messageAttributes()).containsKey("traceparent");
              assertThat(message.attributesAsStrings()).containsKey("AWSTraceHeader");
            });
    assertThat(response.messages())
        .filteredOn(message -> "invalid-traceparent".equals(message.body()))
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.messageAttributes().get("traceparent").stringValue())
                  .isEqualTo(INVALID_TRACEPARENT);
              assertThat(message.attributesAsStrings()).containsKey("AWSTraceHeader");
            });
  }
}
