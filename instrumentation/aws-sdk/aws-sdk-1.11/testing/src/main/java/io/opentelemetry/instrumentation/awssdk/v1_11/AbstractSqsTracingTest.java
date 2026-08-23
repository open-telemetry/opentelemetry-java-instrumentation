/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.message.MessageHeaderUtil.headerAttributeKey;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_SQS_QUEUE_URL;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractStringAssert;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractSqsTracingTest {

  private static int sqsPort;
  private static SQSRestServer sqsRestServer;
  private static AmazonSQSAsync sqsClient;

  protected abstract InstrumentationExtension testing();

  protected abstract AmazonSQSAsyncClientBuilder configureClient(
      AmazonSQSAsyncClientBuilder client);

  @BeforeEach
  void setUp() {
    sqsPort = PortUtils.findOpenPort();
    sqsRestServer = SQSRestServerBuilder.withPort(sqsPort).withInterface("localhost").start();

    AWSStaticCredentialsProvider credentials =
        new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x"));
    AwsClientBuilder.EndpointConfiguration endpointConfiguration =
        new AwsClientBuilder.EndpointConfiguration("http://localhost:" + sqsPort, "elasticmq");

    sqsClient =
        configureClient(AmazonSQSAsyncClient.asyncBuilder())
            .withCredentials(credentials)
            .withEndpointConfiguration(endpointConfiguration)
            .build();
  }

  @AfterEach
  void cleanUp() {
    if (sqsRestServer != null) {
      sqsRestServer.stopAndWait();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testSimpleSqsProducerConsumerServicesCaptureHeaders(boolean testCaptureHeaders) {
    sqsClient.createQueue("testSdkSqs");

    SendMessageRequest sendMessageRequest =
        new SendMessageRequest(
            "http://localhost:" + sqsPort + "/000000000000/testSdkSqs", "{\"type\": \"hello\"}");

    if (testCaptureHeaders) {
      sendMessageRequest.addMessageAttributesEntry(
          "Test-Message-Header",
          new MessageAttributeValue().withDataType("String").withStringValue("test"));
      sendMessageRequest.addMessageAttributesEntry(
          "Uncaptured-Header",
          new MessageAttributeValue().withDataType("String").withStringValue("password"));
    }
    sqsClient.sendMessage(sendMessageRequest);

    ReceiveMessageRequest receiveMessageRequest =
        new ReceiveMessageRequest("http://localhost:" + sqsPort + "/000000000000/testSdkSqs");
    if (testCaptureHeaders) {
      receiveMessageRequest.withMessageAttributeNames("Test-Message-Header", "Uncaptured-Header");
    }
    ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);

    // test different ways of iterating the messages list
    if (testCaptureHeaders) {
      for (Message unused : receiveMessageResult.getMessages()) {
        testing().runWithSpan("process child", () -> {});
      }
    } else {
      receiveMessageResult
          .getMessages()
          .forEach(message -> testing().runWithSpan("process child", () -> {}));
    }

    // without an ambient span the process span is parented to the message creation context, which
    // puts it in the same trace as the publish span
    boolean processInPublishTrace = emitStableMessagingSemconv();
    AtomicReference<SpanData> publishSpan = new AtomicReference<>();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SQS.CreateQueue")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(stringKey("aws.queue.name"), "testSdkSqs"),
                                satisfies(
                                    AWS_REQUEST_ID, AbstractSqsTracingTest::assertAwsRequestId),
                                equalTo(RPC_SYSTEM, "aws-api"),
                                equalTo(RPC_SERVICE, "AmazonSQS"),
                                equalTo(RPC_METHOD, "CreateQueue"),
                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                equalTo(SERVER_PORT, sqsPort),
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"))),
            trace -> {
              publishSpan.set(trace.getSpan(0));

              List<Consumer<SpanDataAssert>> spanAsserts = new ArrayList<>();
              spanAsserts.add(span -> assertPublishSpan(span, testCaptureHeaders));
              if (processInPublishTrace) {
                spanAsserts.add(
                    span ->
                        assertProcessSpan(
                            span, trace.getSpan(0), publishSpan.get(), testCaptureHeaders));
                spanAsserts.add(
                    span ->
                        span.hasName("process child")
                            .hasParent(trace.getSpan(1))
                            .hasTotalAttributeCount(0));
              }
              trace.hasSpansSatisfyingExactly(spanAsserts);
            },
            trace -> {
              List<Consumer<SpanDataAssert>> spanAsserts = new ArrayList<>();
              spanAsserts.add(
                  span -> assertReceiveSpan(span, publishSpan.get(), testCaptureHeaders));
              if (!processInPublishTrace) {
                spanAsserts.add(
                    span -> assertProcessSpan(span, trace.getSpan(0), null, testCaptureHeaders));
                spanAsserts.add(
                    span ->
                        span.hasName("process child")
                            .hasParent(trace.getSpan(1))
                            .hasTotalAttributeCount(0));
              }
              trace.hasSpansSatisfyingExactly(spanAsserts);
            });
  }

  private static void assertPublishSpan(SpanDataAssert span, boolean captureHeaders) {
    List<AttributeAssertion> attributes =
        new ArrayList<>(
            asList(
                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                equalTo(
                    AWS_SQS_QUEUE_URL, "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
                satisfies(AWS_REQUEST_ID, AbstractSqsTracingTest::assertAwsRequestId),
                equalTo(RPC_SYSTEM, "aws-api"),
                equalTo(RPC_SERVICE, "AmazonSQS"),
                equalTo(RPC_METHOD, "SendMessage"),
                equalTo(HTTP_REQUEST_METHOD, "POST"),
                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                equalTo(URL_FULL, "http://localhost:" + sqsPort),
                equalTo(SERVER_ADDRESS, "localhost"),
                equalTo(SERVER_PORT, sqsPort),
                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                equalTo(NETWORK_PROTOCOL_VERSION, "1.1")));

    if (emitStableMessagingSemconv()) {
      attributes.add(equalTo(MESSAGING_OPERATION_NAME, "send"));
      attributes.add(equalTo(MESSAGING_OPERATION_TYPE, "send"));
    }
    if (emitOldMessagingSemconv()) {
      attributes.add(equalTo(MESSAGING_OPERATION, "publish"));
    }

    if (captureHeaders) {
      attributes.add(
          satisfies(headerAttributeKey("Test-Message-Header"), val -> val.containsExactly("test")));
    }

    span.hasName(emitStableMessagingSemconv() ? "send testSdkSqs" : "testSdkSqs publish")
        .hasKind(SpanKind.PRODUCER)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(attributes);
  }

  private static void assertReceiveSpan(
      SpanDataAssert span, SpanData creationContext, boolean captureHeaders) {
    assertReceiveSpan(span, null, creationContext, captureHeaders);
  }

  private static void assertReceiveSpan(
      SpanDataAssert span, SpanData parent, SpanData creationContext, boolean captureHeaders) {
    List<AttributeAssertion> attributes =
        new ArrayList<>(
            asList(
                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                equalTo(
                    AWS_SQS_QUEUE_URL, "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
                satisfies(AWS_REQUEST_ID, AbstractSqsTracingTest::assertAwsRequestId),
                equalTo(RPC_SYSTEM, "aws-api"),
                equalTo(RPC_SERVICE, "AmazonSQS"),
                equalTo(RPC_METHOD, "ReceiveMessage"),
                equalTo(HTTP_REQUEST_METHOD, "POST"),
                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                equalTo(URL_FULL, "http://localhost:" + sqsPort),
                equalTo(SERVER_ADDRESS, "localhost"),
                equalTo(SERVER_PORT, sqsPort),
                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
                equalTo(NETWORK_PROTOCOL_VERSION, "1.1")));

    if (emitStableMessagingSemconv()) {
      attributes.add(equalTo(MESSAGING_OPERATION_NAME, "receive"));
      attributes.add(equalTo(MESSAGING_OPERATION_TYPE, "receive"));
    }
    if (emitOldMessagingSemconv()) {
      attributes.add(equalTo(MESSAGING_OPERATION, "receive"));
    }

    if (captureHeaders) {
      attributes.add(
          satisfies(headerAttributeKey("Test-Message-Header"), val -> val.containsExactly("test")));
    }

    span.hasName(emitStableMessagingSemconv() ? "receive testSdkSqs" : "testSdkSqs receive")
        .hasKind(emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER)
        .hasAttributesSatisfyingExactly(attributes);

    if (parent == null) {
      span.hasNoParent();
    } else {
      span.hasParent(parent);
    }

    if (emitStableMessagingSemconv()) {
      span.hasLinksSatisfying(
          links ->
              assertThat(links)
                  .singleElement()
                  .satisfies(
                      link ->
                          assertThat(link.getSpanContext().getSpanId())
                              .isEqualTo(creationContext.getSpanId())));
    }
  }

  private static void assertProcessSpan(
      SpanDataAssert span, SpanData parent, SpanData creationContext, boolean captureHeaders) {
    List<AttributeAssertion> attributes =
        new ArrayList<>(
            asList(
                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                equalTo(
                    AWS_SQS_QUEUE_URL, "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
                satisfies(AWS_REQUEST_ID, AbstractSqsTracingTest::assertAwsRequestId),
                equalTo(RPC_SYSTEM, "aws-api"),
                equalTo(RPC_SERVICE, "AmazonSQS"),
                equalTo(RPC_METHOD, "ReceiveMessage"),
                equalTo(HTTP_REQUEST_METHOD, "POST"),
                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                equalTo(URL_FULL, "http://localhost:" + sqsPort),
                equalTo(SERVER_ADDRESS, "localhost"),
                equalTo(SERVER_PORT, sqsPort),
                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                equalTo(NETWORK_PROTOCOL_VERSION, "1.1")));

    if (emitStableMessagingSemconv()) {
      attributes.add(equalTo(MESSAGING_OPERATION_NAME, "process"));
      attributes.add(equalTo(MESSAGING_OPERATION_TYPE, "process"));
    }
    if (emitOldMessagingSemconv()) {
      attributes.add(equalTo(MESSAGING_OPERATION, "process"));
    }

    if (captureHeaders) {
      attributes.add(
          satisfies(headerAttributeKey("Test-Message-Header"), val -> val.containsExactly("test")));
    }

    span.hasName(emitStableMessagingSemconv() ? "process testSdkSqs" : "testSdkSqs process")
        .hasKind(SpanKind.CONSUMER)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(attributes);

    if (creationContext != null) {
      span.hasLinksSatisfying(
          links ->
              assertThat(links)
                  .singleElement()
                  .satisfies(
                      link ->
                          assertThat(link.getSpanContext().getSpanId())
                              .isEqualTo(creationContext.getSpanId())));
    }
  }

  @Test
  void testReceiveSpanLinksToProducer() {
    assumeTrue(emitStableMessagingSemconv());
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
    sqsClient.createQueue("testSdkSqs");
    sqsClient.sendMessage(new SendMessageRequest(queueUrl, "hello"));
    ReceiveMessageResult response = sqsClient.receiveMessage(queueUrl);
    String messageId = response.getMessages().get(0).getMessageId();
    // iterating the returned message list is what starts the process spans
    response.getMessages().forEach(unused -> {});

    AtomicReference<SpanData> publishSpan = new AtomicReference<>();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("SQS.CreateQueue").hasKind(SpanKind.CLIENT)),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      publishSpan.set(trace.getSpan(0));
                      span.hasName("send testSdkSqs").hasKind(SpanKind.PRODUCER);
                    },
                    // the process span accounts for a single message, so its link carries no
                    // per-message attributes
                    span ->
                        span.hasName("process testSdkSqs")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasLinksSatisfying(
                                links ->
                                    assertThat(links)
                                        .singleElement()
                                        .satisfies(
                                            link -> assertBareLink(link, publishSpan.get())))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("receive testSdkSqs")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasLinksSatisfying(
                                links ->
                                    assertThat(links)
                                        .singleElement()
                                        .satisfies(
                                            link ->
                                                assertMessageLink(
                                                    link, publishSpan.get(), messageId)))));
  }

  @Test
  void testBatchReceiveLinkAttributes() {
    assumeTrue(emitStableMessagingSemconv());
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
    sqsClient.createQueue("testSdkSqs");
    sqsClient.sendMessageBatch(
        new SendMessageBatchRequest()
            .withQueueUrl(queueUrl)
            .withEntries(
                new SendMessageBatchRequestEntry("i1", "e1"),
                new SendMessageBatchRequestEntry("i2", "e2"),
                new SendMessageBatchRequestEntry("i3", "e3")));

    ReceiveMessageResult response =
        sqsClient.receiveMessage(
            new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(3).withWaitTimeSeconds(5));
    assertThat(response.getMessages()).hasSize(3);

    List<String> messageIds = new ArrayList<>();
    for (int i = 0; i < response.getMessages().size(); i++) {
      messageIds.add(response.getMessages().get(i).getMessageId());
    }

    AtomicReference<SpanData> publishSpan = new AtomicReference<>();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("SQS.CreateQueue").hasKind(SpanKind.CLIENT)),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      publishSpan.set(trace.getSpan(0));
                      span.hasName("send testSdkSqs").hasKind(SpanKind.PRODUCER);
                    }),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("receive testSdkSqs")
                            .hasKind(SpanKind.CLIENT)
                            .hasLinksSatisfying(
                                links -> {
                                  assertThat(links).hasSize(3);
                                  for (int i = 0; i < links.size(); i++) {
                                    assertMessageLink(
                                        links.get(i), publishSpan.get(), messageIds.get(i));
                                  }
                                })));
  }

  @Test
  void testBatchSendMessageCount() {
    assumeTrue(emitStableMessagingSemconv());
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
    sqsClient.createQueue("testSdkSqs");
    sqsClient.sendMessageBatch(
        new SendMessageBatchRequest()
            .withQueueUrl(queueUrl)
            .withEntries(
                new SendMessageBatchRequestEntry("i1", "e1"),
                new SendMessageBatchRequestEntry("i2", "e2"),
                new SendMessageBatchRequestEntry("i3", "e3")));

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
                            .hasAttributesSatisfyingExactly(
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(AWS_SQS_QUEUE_URL, queueUrl),
                                satisfies(
                                    AWS_REQUEST_ID, AbstractSqsTracingTest::assertAwsRequestId),
                                equalTo(RPC_SYSTEM, "aws-api"),
                                equalTo(RPC_SERVICE, "AmazonSQS"),
                                equalTo(RPC_METHOD, "SendMessageBatch"),
                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                equalTo(SERVER_PORT, sqsPort),
                                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                equalTo(MESSAGING_OPERATION_NAME, "send"),
                                equalTo(MESSAGING_OPERATION_TYPE, "send"),
                                equalTo(
                                    MESSAGING_OPERATION,
                                    emitOldMessagingSemconv() ? "publish" : null),
                                equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 3),
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"))));
  }

  @Test
  void testDeleteMessage() {
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
    String receiptHandle = createMessages(queueUrl, 1).get(0);

    sqsClient.deleteMessage(new DeleteMessageRequest(queueUrl, receiptHandle));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attributes =
                          new ArrayList<>(
                              asList(
                                  equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                  equalTo(AWS_SQS_QUEUE_URL, queueUrl),
                                  satisfies(
                                      AWS_REQUEST_ID, AbstractSqsTracingTest::assertAwsRequestId),
                                  equalTo(RPC_SYSTEM, "aws-api"),
                                  equalTo(RPC_SERVICE, "AmazonSQS"),
                                  equalTo(RPC_METHOD, "DeleteMessage"),
                                  equalTo(HTTP_REQUEST_METHOD, "POST"),
                                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                  equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                  equalTo(SERVER_ADDRESS, "localhost"),
                                  equalTo(SERVER_PORT, sqsPort),
                                  equalTo(NETWORK_PROTOCOL_VERSION, "1.1")));
                      if (emitStableMessagingSemconv()) {
                        attributes.add(equalTo(MESSAGING_SYSTEM, AWS_SQS));
                        attributes.add(equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"));
                        attributes.add(equalTo(MESSAGING_OPERATION_NAME, "delete"));
                        attributes.add(equalTo(MESSAGING_OPERATION_TYPE, "settle"));
                        attributes.add(
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "settle" : null));
                      }

                      span.hasName(
                              emitStableMessagingSemconv()
                                  ? "delete testSdkSqs"
                                  : "SQS.DeleteMessage")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attributes);
                    }));
  }

  @Test
  void testDeleteMessageBatch() {
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
    List<String> receiptHandles = createMessages(queueUrl, 2);

    sqsClient.deleteMessageBatch(
        new DeleteMessageBatchRequest()
            .withQueueUrl(queueUrl)
            .withEntries(
                new DeleteMessageBatchRequestEntry("i1", receiptHandles.get(0)),
                new DeleteMessageBatchRequestEntry("i2", receiptHandles.get(1))));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attributes =
                          new ArrayList<>(
                              asList(
                                  equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                  equalTo(AWS_SQS_QUEUE_URL, queueUrl),
                                  satisfies(
                                      AWS_REQUEST_ID, AbstractSqsTracingTest::assertAwsRequestId),
                                  equalTo(RPC_SYSTEM, "aws-api"),
                                  equalTo(RPC_SERVICE, "AmazonSQS"),
                                  equalTo(RPC_METHOD, "DeleteMessageBatch"),
                                  equalTo(HTTP_REQUEST_METHOD, "POST"),
                                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                  equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                  equalTo(SERVER_ADDRESS, "localhost"),
                                  equalTo(SERVER_PORT, sqsPort),
                                  equalTo(NETWORK_PROTOCOL_VERSION, "1.1")));
                      if (emitStableMessagingSemconv()) {
                        attributes.add(equalTo(MESSAGING_SYSTEM, AWS_SQS));
                        attributes.add(equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"));
                        attributes.add(equalTo(MESSAGING_OPERATION_NAME, "delete"));
                        attributes.add(equalTo(MESSAGING_OPERATION_TYPE, "settle"));
                        attributes.add(
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "settle" : null));
                        attributes.add(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 2));
                      }

                      span.hasName(
                              emitStableMessagingSemconv()
                                  ? "delete testSdkSqs"
                                  : "SQS.DeleteMessageBatch")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attributes);
                    }));
  }

  @Test
  void testChangeMessageVisibilityUsesGenericRpcSpan() {
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
    String receiptHandle = createMessages(queueUrl, 1).get(0);

    sqsClient.changeMessageVisibility(
        new ChangeMessageVisibilityRequest()
            .withQueueUrl(queueUrl)
            .withReceiptHandle(receiptHandle)
            .withVisibilityTimeout(1));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attributes =
                          new ArrayList<>(
                              asList(
                                  equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                  equalTo(AWS_SQS_QUEUE_URL, queueUrl),
                                  satisfies(
                                      AWS_REQUEST_ID, AbstractSqsTracingTest::assertAwsRequestId),
                                  equalTo(RPC_SYSTEM, "aws-api"),
                                  equalTo(RPC_SERVICE, "AmazonSQS"),
                                  equalTo(RPC_METHOD, "ChangeMessageVisibility"),
                                  equalTo(HTTP_REQUEST_METHOD, "POST"),
                                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                  equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                  equalTo(SERVER_ADDRESS, "localhost"),
                                  equalTo(SERVER_PORT, sqsPort),
                                  equalTo(NETWORK_PROTOCOL_VERSION, "1.1")));

                      span.hasName("SQS.ChangeMessageVisibility")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attributes);
                    }));
  }

  @Test
  void testChangeMessageVisibilityBatchUsesGenericRpcSpan() {
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
    List<String> receiptHandles = createMessages(queueUrl, 2);

    sqsClient.changeMessageVisibilityBatch(
        new ChangeMessageVisibilityBatchRequest()
            .withQueueUrl(queueUrl)
            .withEntries(
                new ChangeMessageVisibilityBatchRequestEntry()
                    .withId("i1")
                    .withReceiptHandle(receiptHandles.get(0))
                    .withVisibilityTimeout(1),
                new ChangeMessageVisibilityBatchRequestEntry()
                    .withId("i2")
                    .withReceiptHandle(receiptHandles.get(1))
                    .withVisibilityTimeout(1)));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attributes =
                          new ArrayList<>(
                              asList(
                                  equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                  equalTo(AWS_SQS_QUEUE_URL, queueUrl),
                                  satisfies(
                                      AWS_REQUEST_ID, AbstractSqsTracingTest::assertAwsRequestId),
                                  equalTo(RPC_SYSTEM, "aws-api"),
                                  equalTo(RPC_SERVICE, "AmazonSQS"),
                                  equalTo(RPC_METHOD, "ChangeMessageVisibilityBatch"),
                                  equalTo(HTTP_REQUEST_METHOD, "POST"),
                                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                  equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                  equalTo(SERVER_ADDRESS, "localhost"),
                                  equalTo(SERVER_PORT, sqsPort),
                                  equalTo(NETWORK_PROTOCOL_VERSION, "1.1")));

                      span.hasName("SQS.ChangeMessageVisibilityBatch")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attributes);
                    }));
  }

  @Test
  void testDeleteMessageError() {
    assumeTrue(emitStableMessagingSemconv());
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/missing";

    Throwable error =
        catchThrowable(
            () -> sqsClient.deleteMessage(new DeleteMessageRequest(queueUrl, "receipt-handle")));

    assertThat(error).isInstanceOf(QueueDoesNotExistException.class);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("delete missing")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(AWS_SQS_QUEUE_URL, queueUrl),
                                equalTo(RPC_SYSTEM, "aws-api"),
                                equalTo(RPC_SERVICE, "AmazonSQS"),
                                equalTo(RPC_METHOD, "DeleteMessage"),
                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                equalTo(SERVER_PORT, sqsPort),
                                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                equalTo(MESSAGING_DESTINATION_NAME, "missing"),
                                equalTo(MESSAGING_OPERATION_NAME, "delete"),
                                equalTo(MESSAGING_OPERATION_TYPE, "settle"),
                                equalTo(
                                    MESSAGING_OPERATION,
                                    emitOldMessagingSemconv() ? "settle" : null),
                                equalTo(ERROR_TYPE, QueueDoesNotExistException.class.getName()))));
  }

  private List<String> createMessages(String queueUrl, int count) {
    sqsClient.createQueue("testSdkSqs");
    for (int i = 0; i < count; i++) {
      sqsClient.sendMessage(new SendMessageRequest(queueUrl, "message-" + i));
    }
    List<String> receiptHandles =
        sqsClient
            .receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(count))
            .getMessages()
            .stream()
            .map(Message::getReceiptHandle)
            .collect(toList());
    assertThat(receiptHandles).hasSize(count);
    testing().clearData();
    return receiptHandles;
  }

  @Test
  void testSimpleSqsProducerConsumerServicesWithParentSpan() {
    sqsClient.createQueue("testSdkSqs");
    SendMessageRequest sendMessageRequest =
        new SendMessageRequest(
            "http://localhost:" + sqsPort + "/000000000000/testSdkSqs", "{\"type\": \"hello\"}");
    sqsClient.sendMessage(sendMessageRequest);

    testing()
        .runWithSpan(
            "parent",
            () -> {
              ReceiveMessageResult receiveMessageResult =
                  sqsClient.receiveMessage(
                      "http://localhost:" + sqsPort + "/000000000000/testSdkSqs");
              receiveMessageResult
                  .getMessages()
                  .forEach(message -> testing().runWithSpan("process child", () -> {}));
            });

    if (emitStableMessagingSemconv()) {
      AtomicReference<SpanData> producerSpan = new AtomicReference<>();
      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("SQS.CreateQueue").hasKind(SpanKind.CLIENT)),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> {
                        producerSpan.set(trace.getSpan(0));
                        span.hasName("send testSdkSqs").hasKind(SpanKind.PRODUCER);
                      }),
              trace -> {
                Consumer<SpanDataAssert> receiveSpanAssertion =
                    span -> assertReceiveSpan(span, trace.getSpan(0), producerSpan.get(), false);
                Consumer<SpanDataAssert> sdkSpanAssertion =
                    span ->
                        span.hasName("SQS.ReceiveMessage")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0));
                Consumer<SpanDataAssert> processSpanAssertion =
                    span -> assertProcessSpan(span, trace.getSpan(0), producerSpan.get(), false);

                List<Consumer<SpanDataAssert>> assertions =
                    new ArrayList<>(
                        asList(
                            span -> span.hasName("parent").hasNoParent(),
                            receiveSpanAssertion,
                            sdkSpanAssertion,
                            processSpanAssertion,
                            span -> span.hasName("process child")));
                // on jdk8 the order of the "SQS.ReceiveMessage" and "receive testSdkSqs"
                // spans can vary
                if ("SQS.ReceiveMessage".equals(trace.getSpan(1).getName())) {
                  assertions.set(1, sdkSpanAssertion);
                  assertions.set(2, receiveSpanAssertion);
                }
                trace.hasSpansSatisfyingExactly(assertions);
              });
      return;
    }

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SQS.CreateQueue")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(stringKey("aws.queue.name"), "testSdkSqs"),
                                satisfies(
                                    AWS_REQUEST_ID, AbstractSqsTracingTest::assertAwsRequestId),
                                equalTo(RPC_SYSTEM, "aws-api"),
                                equalTo(RPC_SERVICE, "AmazonSQS"),
                                equalTo(RPC_METHOD, "CreateQueue"),
                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                equalTo(SERVER_PORT, sqsPort),
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("testSdkSqs publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(
                                    AWS_SQS_QUEUE_URL,
                                    "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
                                satisfies(
                                    AWS_REQUEST_ID, AbstractSqsTracingTest::assertAwsRequestId),
                                equalTo(RPC_SYSTEM, "aws-api"),
                                equalTo(RPC_SERVICE, "AmazonSQS"),
                                equalTo(RPC_METHOD, "SendMessage"),
                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                equalTo(SERVER_PORT, sqsPort),
                                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                equalTo(MESSAGING_OPERATION, "publish"),
                                satisfies(
                                    MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"))),
            trace -> {
              AtomicReference<SpanData> receiveSpan = new AtomicReference<>();
              AtomicReference<SpanData> processSpan = new AtomicReference<>();

              List<Consumer<SpanDataAssert>> assertions =
                  new ArrayList<>(
                      asList(
                          span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                          span ->
                              span.hasName("SQS.ReceiveMessage")
                                  .hasKind(SpanKind.CLIENT)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                      equalTo(
                                          AWS_SQS_QUEUE_URL,
                                          "http://localhost:"
                                              + sqsPort
                                              + "/000000000000/testSdkSqs"),
                                      satisfies(
                                          AWS_REQUEST_ID,
                                          AbstractSqsTracingTest::assertAwsRequestId),
                                      equalTo(RPC_SYSTEM, "aws-api"),
                                      equalTo(RPC_SERVICE, "AmazonSQS"),
                                      equalTo(RPC_METHOD, "ReceiveMessage"),
                                      equalTo(HTTP_REQUEST_METHOD, "POST"),
                                      equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                      equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                      equalTo(SERVER_ADDRESS, "localhost"),
                                      equalTo(SERVER_PORT, sqsPort),
                                      equalTo(NETWORK_PROTOCOL_VERSION, "1.1")),
                          span ->
                              span.hasName("testSdkSqs receive")
                                  .hasKind(SpanKind.CONSUMER)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                      equalTo(
                                          AWS_SQS_QUEUE_URL,
                                          "http://localhost:"
                                              + sqsPort
                                              + "/000000000000/testSdkSqs"),
                                      satisfies(
                                          AWS_REQUEST_ID,
                                          AbstractSqsTracingTest::assertAwsRequestId),
                                      equalTo(RPC_SYSTEM, "aws-api"),
                                      equalTo(RPC_SERVICE, "AmazonSQS"),
                                      equalTo(RPC_METHOD, "ReceiveMessage"),
                                      equalTo(HTTP_REQUEST_METHOD, "POST"),
                                      equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                      equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                      equalTo(SERVER_ADDRESS, "localhost"),
                                      equalTo(SERVER_PORT, sqsPort),
                                      equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                      equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                      equalTo(MESSAGING_OPERATION, "receive"),
                                      equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
                                      equalTo(NETWORK_PROTOCOL_VERSION, "1.1")),
                          span ->
                              span.hasName("testSdkSqs process")
                                  .hasKind(SpanKind.CONSUMER)
                                  .hasParent(receiveSpan.get())
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                      equalTo(
                                          AWS_SQS_QUEUE_URL,
                                          "http://localhost:"
                                              + sqsPort
                                              + "/000000000000/testSdkSqs"),
                                      satisfies(
                                          AWS_REQUEST_ID,
                                          AbstractSqsTracingTest::assertAwsRequestId),
                                      equalTo(RPC_SYSTEM, "aws-api"),
                                      equalTo(RPC_SERVICE, "AmazonSQS"),
                                      equalTo(RPC_METHOD, "ReceiveMessage"),
                                      equalTo(HTTP_REQUEST_METHOD, "POST"),
                                      equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                      equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                      equalTo(SERVER_ADDRESS, "localhost"),
                                      equalTo(SERVER_PORT, sqsPort),
                                      equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                      equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                      equalTo(MESSAGING_OPERATION, "process"),
                                      satisfies(
                                          MESSAGING_MESSAGE_ID,
                                          val -> val.isInstanceOf(String.class)),
                                      equalTo(NETWORK_PROTOCOL_VERSION, "1.1")),
                          span ->
                              span.hasName("process child")
                                  .hasParent(processSpan.get())
                                  .hasTotalAttributeCount(0)));

              // on jdk8 the order of the "SQS.ReceiveMessage" and "testSdkSqs receive"
              // spans can vary
              if ("SQS.ReceiveMessage".equals(trace.getSpan(1).getName())) {
                receiveSpan.set(trace.getSpan(2));
                processSpan.set(trace.getSpan(3));
              } else {
                receiveSpan.set(trace.getSpan(1));
                processSpan.set(trace.getSpan(2));

                // move "SQS.ReceiveMessage" assertions to the last position
                assertions.add(assertions.remove(1));
              }

              trace.hasSpansSatisfyingExactly(assertions);
            });
  }

  @Test
  void testOnlyAddsAttributeNameOnceWhenRequestReused() {
    sqsClient.createQueue("testSdkSqs2");
    SendMessageRequest send =
        new SendMessageRequest(
            "http://localhost:" + sqsPort + "/000000000000/testSdkSqs2", "{\"type\": \"hello\"}");
    sqsClient.sendMessage(send);
    ReceiveMessageRequest receive =
        new ReceiveMessageRequest("http://localhost:" + sqsPort + "/000000000000/testSdkSqs2");
    sqsClient.receiveMessage(receive);
    sqsClient.sendMessage(send);
    sqsClient.receiveMessage(receive);
    assertThat(receive.getAttributeNames()).containsExactly("AWSTraceHeader");
  }

  @Test
  void testProducerMetrics() {
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
    sqsClient.createQueue("testSdkSqs");
    testing().clearData();

    sqsClient.sendMessage(new SendMessageRequest(queueUrl, "single"));
    sqsClient.sendMessageBatch(
        new SendMessageBatchRequest()
            .withQueueUrl(queueUrl)
            .withEntries(
                new SendMessageBatchRequestEntry("i1", "e1"),
                new SendMessageBatchRequestEntry("i2", "e2"),
                new SendMessageBatchRequestEntry("i3", "e3")));

    SqsMetricsAssertions.assertProducerMetrics(testing(), sqsPort, 2, 4);
  }

  @Test
  void testReceiveAndProcessMetrics() {
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
    sqsClient.createQueue("testSdkSqs");
    sqsClient.sendMessageBatch(
        new SendMessageBatchRequest()
            .withQueueUrl(queueUrl)
            .withEntries(
                new SendMessageBatchRequestEntry("i1", "e1"),
                new SendMessageBatchRequestEntry("i2", "e2"),
                new SendMessageBatchRequestEntry("i3", "e3")));
    testing().clearData();

    ReceiveMessageResult response =
        sqsClient.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(10));
    response.getMessages().forEach(message -> {});
    ReceiveMessageResult emptyResponse =
        sqsClient.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(10));

    assertThat(response.getMessages()).hasSize(3);
    assertThat(emptyResponse.getMessages()).isEmpty();
    // the poll that returned no messages is not instrumented, so only one receive operation is
    // recorded
    SqsMetricsAssertions.assertReceiveAndProcessMetrics(testing(), sqsPort, 1, 3);
  }

  @Test
  void testSettleMetrics() {
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
    List<String> receiptHandles = createMessages(queueUrl, 2);

    sqsClient.deleteMessage(new DeleteMessageRequest(queueUrl, receiptHandles.get(0)));
    sqsClient.deleteMessageBatch(
        new DeleteMessageBatchRequest()
            .withQueueUrl(queueUrl)
            .withEntries(new DeleteMessageBatchRequestEntry("i1", receiptHandles.get(1))));

    SqsMetricsAssertions.assertSettleMetrics(testing(), sqsPort, 2);
  }

  static void assertAwsRequestId(AbstractStringAssert<?> val) {
    if (testLatestDeps()) {
      val.isNull();
    } else {
      val.isInstanceOf(String.class);
    }
  }

  private static void assertMessageLink(LinkData link, SpanData creationContext, String messageId) {
    assertThat(link.getSpanContext().getSpanId()).isEqualTo(creationContext.getSpanId());
    assertThat(link.getAttributes()).isEqualTo(Attributes.of(MESSAGING_MESSAGE_ID, messageId));
  }

  private static void assertBareLink(LinkData link, SpanData creationContext) {
    assertThat(link.getSpanContext().getSpanId()).isEqualTo(creationContext.getSpanId());
    assertThat(link.getAttributes()).isEqualTo(Attributes.empty());
  }
}
