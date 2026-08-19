/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SqsTracingTest extends AbstractSqsTracingTest {

  private static final String CUSTOM_XRAY_CONTEXT =
      "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1";

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  public AmazonSQSAsyncClientBuilder configureClient(AmazonSQSAsyncClientBuilder client) {
    return client.withRequestHandlers(
        AwsSdkTelemetry.builder(testing().getOpenTelemetry())
            .setCaptureExperimentalSpanAttributes(true)
            .setMessagingReceiveTelemetryEnabled(true)
            .setHeaders(
                IncludeExclude.builder()
                    .setIncluded("Test-Message-*")
                    .setExcluded("*-Excluded-Header")
                    .build())
            .build()
            .createRequestHandler());
  }

  @Test
  void testDisableSqsMessageCreateSpans() {
    assumeTrue(emitStableMessagingSemconv());
    AmazonSQSAsync client =
        newClientBuilder()
            .withRequestHandlers(
                AwsSdkTelemetry.builder(testing().getOpenTelemetry())
                    .setCaptureExperimentalSpanAttributes(true)
                    .setMessageCreateSpansEnabled(false)
                    .build()
                    .createRequestHandler())
            .build();
    try {
      String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
      client.createQueue("testSdkSqs");
      client.sendMessageBatch(
          new SendMessageBatchRequest()
              .withQueueUrl(queueUrl)
              .withEntries(
                  new SendMessageBatchRequestEntry("i1", "e1"),
                  new SendMessageBatchRequestEntry("i2", "e2")));

      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("SQS.CreateQueue").hasKind(SpanKind.CLIENT)),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("send testSdkSqs")
                              .hasKind(SpanKind.PRODUCER)
                              .hasNoParent()
                              .hasTotalRecordedLinks(0)));
    } finally {
      client.shutdown();
    }
  }

  @Test
  void testBatchLinksSurviveLaterRequestClone() {
    assumeTrue(emitStableMessagingSemconv());
    RequestHandler2 tracingHandler =
        AwsSdkTelemetry.builder(testing().getOpenTelemetry()).build().createRequestHandler();
    RequestHandler2 cloningHandler =
        new RequestHandler2() {
          @Override
          public AmazonWebServiceRequest beforeMarshalling(AmazonWebServiceRequest request) {
            return request instanceof SendMessageBatchRequest
                ? ((SendMessageBatchRequest) request).clone()
                : request;
          }
        };
    AmazonSQSAsync client =
        newClientBuilder().withRequestHandlers(tracingHandler, cloningHandler).build();
    try {
      String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
      client.createQueue("testSdkSqs");
      client.sendMessageBatch(
          new SendMessageBatchRequest()
              .withQueueUrl(queueUrl)
              .withEntries(
                  new SendMessageBatchRequestEntry("i1", "e1"),
                  new SendMessageBatchRequestEntry("i2", "e2")));

      AtomicReference<SpanData> firstCreateSpan = new AtomicReference<>();
      AtomicReference<SpanData> secondCreateSpan = new AtomicReference<>();
      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("SQS.CreateQueue").hasKind(SpanKind.CLIENT)),
              trace -> {
                firstCreateSpan.set(trace.getSpan(0));
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("create testSdkSqs").hasKind(SpanKind.PRODUCER));
              },
              trace -> {
                secondCreateSpan.set(trace.getSpan(0));
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
                                              firstCreateSpan.get().getSpanId(),
                                              secondCreateSpan.get().getSpanId()))));
    } finally {
      client.shutdown();
    }
  }

  @Test
  void testPreservesCustomBatchCreationContext() {
    assumeTrue(emitStableMessagingSemconv());
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
    AmazonSQSAsync client = configureClient(newClientBuilder()).build();
    try {
      client.createQueue("testSdkSqs");
      SendMessageBatchRequest batchRequest =
          new SendMessageBatchRequest()
              .withQueueUrl(queueUrl)
              .withEntries(
                  new SendMessageBatchRequestEntry("i1", "e1")
                      .addMessageAttributesEntry(
                          "X-Amzn-Trace-Id",
                          new MessageAttributeValue()
                              .withDataType("String")
                              .withStringValue(CUSTOM_XRAY_CONTEXT)),
                  new SendMessageBatchRequestEntry("i2", "e2"));

      client.sendMessageBatch(batchRequest);

      AtomicReference<SpanData> createSpan = new AtomicReference<>();
      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("SQS.CreateQueue").hasKind(SpanKind.CLIENT)),
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
                                              "53995c3f42cd8ad8", createSpan.get().getSpanId()))));

      ReceiveMessageResult result =
          client.receiveMessage(
              new ReceiveMessageRequest(queueUrl)
                  .withMaxNumberOfMessages(2)
                  .withMessageAttributeNames("All"));
      assertThat(result.getMessages())
          .extracting(
              message -> {
                MessageAttributeValue value = message.getMessageAttributes().get("X-Amzn-Trace-Id");
                return value == null ? null : value.getStringValue();
              })
          .contains(CUSTOM_XRAY_CONTEXT);
    } finally {
      client.shutdown();
    }
  }
}
