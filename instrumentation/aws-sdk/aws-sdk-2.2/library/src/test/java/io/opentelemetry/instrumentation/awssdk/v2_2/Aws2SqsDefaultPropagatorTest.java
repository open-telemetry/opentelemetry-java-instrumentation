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
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

class Aws2SqsDefaultPropagatorTest extends Aws2SqsTracingTest {

  @Override
  void configure(AwsSdkTelemetryBuilder telemetryBuilder) {}

  @Override
  protected boolean isSqsAttributeInjectionEnabled() {
    return false;
  }

  @Test
  void testDuplicateTracingInterceptor() throws URISyntaxException {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    ClientOverrideConfiguration overrideConfiguration =
        ClientOverrideConfiguration.builder()
            .addExecutionInterceptor(telemetry.createExecutionInterceptor())
            .addExecutionInterceptor(telemetry.createExecutionInterceptor())
            .build();

    builder.overrideConfiguration(overrideConfiguration);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);
    client.sendMessage(sendMessageRequest);
    ReceiveMessageResponse response = client.receiveMessage(receiveMessageRequest);

    assertThat(response.messages()).hasSize(1);
    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));
    assertSqsTraces(false, false);
  }

  @Test
  void testDisableSqsMessageCreateSpans() {
    assumeTrue(emitStableMessagingSemconv());
    AwsSdkTelemetry disabledTelemetry =
        AwsSdkTelemetry.builder(getTesting().getOpenTelemetry())
            .setCaptureExperimentalSpanAttributes(true)
            .setMessageCreateSpansEnabled(false)
            .build();
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    builder.overrideConfiguration(
        ClientOverrideConfiguration.builder()
            .addExecutionInterceptor(disabledTelemetry.createExecutionInterceptor())
            .build());

    try (SqsClient client = disabledTelemetry.wrap(builder.build())) {
      client.createQueue(createQueueRequest);
      client.sendMessageBatch(sendMessageBatchRequest);

      getTesting()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("Sqs.CreateQueue").hasKind(SpanKind.CLIENT)),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("send testSdkSqs")
                              .hasKind(SpanKind.PRODUCER)
                              .hasNoParent()
                              .hasTotalRecordedLinks(0)));
    }
  }

  @Test
  void testDisabledXrayPropagatorDoesNotExtractMessageAttribute() {
    assumeTrue(emitStableMessagingSemconv());
    AwsSdkTelemetry disabledXrayTelemetry =
        AwsSdkTelemetry.builder(getTesting().getOpenTelemetry())
            .setCaptureExperimentalSpanAttributes(true)
            .setMessagingReceiveTelemetryEnabled(true)
            .setUseXrayPropagator(false)
            .build();
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    builder.overrideConfiguration(
        ClientOverrideConfiguration.builder()
            .addExecutionInterceptor(disabledXrayTelemetry.createExecutionInterceptor())
            .build());

    try (SqsClient client = disabledXrayTelemetry.wrap(builder.build())) {
      client.createQueue(createQueueRequest);
      client.sendMessage(
          SendMessageRequest.builder()
              .queueUrl(queueUrl)
              .messageBody("test")
              .messageAttributes(
                  singletonMap(
                      "X-Amzn-Trace-Id",
                      MessageAttributeValue.builder()
                          .dataType("String")
                          .stringValue(
                              "Root=1-5759e988-bd862e3fe1be46a994272793;"
                                  + "Parent=53995c3f42cd8ad8;Sampled=1")
                          .build()))
              .build());
      ReceiveMessageResponse response =
          client.receiveMessage(
              ReceiveMessageRequest.builder()
                  .queueUrl(queueUrl)
                  .messageAttributeNames("All")
                  .build());
      response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));

      getTesting()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("Sqs.CreateQueue").hasKind(SpanKind.CLIENT)),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("send testSdkSqs").hasKind(SpanKind.PRODUCER).hasNoParent()),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("receive testSdkSqs")
                              .hasKind(SpanKind.CLIENT)
                              .hasNoParent()
                              .hasTotalRecordedLinks(0)),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("process testSdkSqs")
                              .hasKind(SpanKind.CONSUMER)
                              .hasNoParent()
                              .hasTotalRecordedLinks(0),
                      span -> span.hasName("process child").hasParent(trace.getSpan(0))));
    }
  }
}
