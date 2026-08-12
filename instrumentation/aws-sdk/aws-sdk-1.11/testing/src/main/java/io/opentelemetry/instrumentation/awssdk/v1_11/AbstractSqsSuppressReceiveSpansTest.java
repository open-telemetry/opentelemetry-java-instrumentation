/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_SQS_QUEUE_URL;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractSqsSuppressReceiveSpansTest {

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

  @Test
  void testSimpleSqsProducerConsumerServices() {
    sqsClient.createQueue("testSdkSqs");

    SendMessageRequest send =
        new SendMessageRequest(
            "http://localhost:" + sqsPort + "/000000000000/testSdkSqs", "{\"type\": \"hello\"}");
    sqsClient.sendMessage(send);
    ReceiveMessageResult receiveMessageResult =
        sqsClient.receiveMessage("http://localhost:" + sqsPort + "/000000000000/testSdkSqs");
    receiveMessageResult
        .getMessages()
        .forEach(message -> testing().runWithSpan("process child", () -> {}));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    AbstractSqsSuppressReceiveSpansTest::createQueueSpan),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    AbstractSqsSuppressReceiveSpansTest::publishSpan,
                    span -> processSpan(span, trace.getSpan(0), trace.getSpan(0)),
                    span ->
                        span.hasName("process child")
                            .hasParent(trace.getSpan(1))
                            .hasTotalAttributeCount(0)));
  }

  @Test
  void testAbandonedIteratorDoesNotParentNextProcessSpan() {
    assumeTrue(emitStableMessagingSemconv());
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";
    sqsClient.createQueue("testSdkSqs");

    sqsClient.sendMessage(new SendMessageRequest(queueUrl, "first"));
    ReceiveMessageResult firstResponse = sqsClient.receiveMessage(queueUrl);
    sqsClient.sendMessage(new SendMessageRequest(queueUrl, "second"));
    ReceiveMessageResult secondResponse = sqsClient.receiveMessage(queueUrl);

    Iterator<Message> firstIterator = firstResponse.getMessages().iterator();
    Iterator<Message> secondIterator = secondResponse.getMessages().iterator();
    assertThat(firstIterator.hasNext()).isTrue();
    firstIterator.next();
    assertThat(secondIterator.hasNext()).isTrue();
    secondIterator.next();
    assertThat(secondIterator.hasNext()).isFalse();
    assertThat(firstIterator.hasNext()).isFalse();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("SQS.CreateQueue").hasNoParent()),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("send testSdkSqs").hasNoParent(),
                    span -> span.hasName("process testSdkSqs").hasParent(trace.getSpan(0))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("send testSdkSqs").hasNoParent(),
                    span -> span.hasName("process testSdkSqs").hasParent(trace.getSpan(0))));
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

    // with an ambient span the process span is parented to it rather than to the message creation
    // context, which puts it in the same trace as the ambient span
    boolean processInParentTrace = emitStableMessagingSemconv();
    AtomicReference<SpanData> publishSpan = new AtomicReference<>();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    AbstractSqsSuppressReceiveSpansTest::createQueueSpan),
            trace -> {
              List<Consumer<SpanDataAssert>> spanAsserts = new ArrayList<>();
              spanAsserts.add(
                  span -> {
                    publishSpan.set(trace.getSpan(0));
                    publishSpan(span);
                  });
              if (!processInParentTrace) {
                spanAsserts.add(span -> processSpan(span, trace.getSpan(0), trace.getSpan(0)));
                spanAsserts.add(
                    span ->
                        span.hasName("process child")
                            .hasParent(trace.getSpan(1))
                            .hasTotalAttributeCount(0));
              }
              trace.hasSpansSatisfyingExactly(spanAsserts);
            },
            trace -> {
              List<Consumer<SpanDataAssert>> spanAsserts =
                  new ArrayList<>(
                      asList(
                          span -> span.hasName("parent").hasNoParent(),
                          /*
                           * This span represents HTTP "sending of receive message" operation. It's always single,
                           * while there can be multiple CONSUMER spans (one per consumed message).
                           * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then
                           * HTTP instrumentation span would appear)
                           */
                          span -> receiveMessageSdkSpan(span, trace.getSpan(0))));
              if (processInParentTrace) {
                spanAsserts.add(span -> processSpan(span, trace.getSpan(0), publishSpan.get()));
                spanAsserts.add(
                    span ->
                        span.hasName("process child")
                            .hasParent(trace.getSpan(2))
                            .hasTotalAttributeCount(0));
              }
              trace.hasSpansSatisfyingExactly(spanAsserts);
            });
  }

  private static void createQueueSpan(SpanDataAssert span) {
    span.hasName("SQS.CreateQueue")
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(stringKey("aws.queue.name"), "testSdkSqs"),
            satisfies(AWS_REQUEST_ID, AbstractSqsTracingTest::assertAwsRequestId),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "AmazonSQS"),
            equalTo(RPC_METHOD, "CreateQueue"),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            equalTo(URL_FULL, "http://localhost:" + sqsPort),
            equalTo(SERVER_ADDRESS, "localhost"),
            equalTo(SERVER_PORT, sqsPort),
            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
  }

  private static void publishSpan(SpanDataAssert span) {
    span.hasName(emitStableMessagingSemconv() ? "send testSdkSqs" : "testSdkSqs publish")
        .hasKind(SpanKind.PRODUCER)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(AWS_SQS_QUEUE_URL, "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
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
            equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null),
            equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "send" : null),
            equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "send" : null),
            satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
  }

  private static void processSpan(SpanDataAssert span, SpanData parent, SpanData creationContext) {
    span.hasName(emitStableMessagingSemconv() ? "process testSdkSqs" : "testSdkSqs process")
        .hasKind(SpanKind.CONSUMER)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(AWS_SQS_QUEUE_URL, "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
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
            equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "process" : null),
            equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "process" : null),
            equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "process" : null),
            satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));

    if (emitStableMessagingSemconv()) {
      // the creation context is linked even when it is also this span's parent
      span.hasLinksSatisfying(
          links ->
              assertThat(links)
                  .singleElement()
                  .satisfies(
                      link ->
                          assertThat(link.getSpanContext().getSpanId())
                              .isEqualTo(creationContext.getSpanId())));
    } else {
      span.hasTotalRecordedLinks(0);
    }
  }

  private static void receiveMessageSdkSpan(SpanDataAssert span, SpanData parent) {
    span.hasName("SQS.ReceiveMessage")
        .hasKind(SpanKind.CLIENT)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(AWS_SQS_QUEUE_URL, "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
            satisfies(AWS_REQUEST_ID, AbstractSqsTracingTest::assertAwsRequestId),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "AmazonSQS"),
            equalTo(RPC_METHOD, "ReceiveMessage"),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            equalTo(URL_FULL, "http://localhost:" + sqsPort),
            equalTo(SERVER_ADDRESS, "localhost"),
            equalTo(SERVER_PORT, sqsPort),
            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
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
}
