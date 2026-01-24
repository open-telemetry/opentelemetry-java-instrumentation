/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcMethodAssertions;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcSystemAssertion;
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
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractSqsSuppressReceiveSpansTest {

  protected abstract InstrumentationExtension testing();

  protected abstract AmazonSQSAsyncClientBuilder configureClient(
      AmazonSQSAsyncClientBuilder client);

  private static int sqsPort;
  private static SQSRestServer sqsRestServer;
  private static AmazonSQSAsync sqsClient;

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
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
                      attrs.add(equalTo(stringKey("aws.queue.name"), "testSdkSqs"));
                      attrs.add(satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)));
                      attrs.add(rpcSystemAssertion("aws-api"));
                      attrs.addAll(rpcMethodAssertions("AmazonSQS", "CreateQueue"));
                      attrs.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
                      attrs.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                      attrs.add(equalTo(URL_FULL, "http://localhost:" + sqsPort));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, sqsPort));
                      attrs.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
                      span.hasName("SQS.CreateQueue")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attrs);
                    }),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
                      attrs.add(
                          equalTo(
                              AWS_SQS_QUEUE_URL,
                              "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"));
                      attrs.add(satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)));
                      attrs.add(rpcSystemAssertion("aws-api"));
                      attrs.addAll(rpcMethodAssertions("AmazonSQS", "SendMessage"));
                      attrs.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
                      attrs.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                      attrs.add(equalTo(URL_FULL, "http://localhost:" + sqsPort));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, sqsPort));
                      attrs.add(
                          equalTo(
                              MESSAGING_SYSTEM,
                              MessagingIncubatingAttributes.MessagingSystemIncubatingValues
                                  .AWS_SQS));
                      attrs.add(equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"));
                      attrs.add(equalTo(MESSAGING_OPERATION, "publish"));
                      attrs.add(
                          satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)));
                      attrs.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
                      span.hasName("testSdkSqs publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attrs);
                    },
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
                      attrs.add(
                          equalTo(
                              AWS_SQS_QUEUE_URL,
                              "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"));
                      attrs.add(satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)));
                      attrs.add(rpcSystemAssertion("aws-api"));
                      attrs.addAll(rpcMethodAssertions("AmazonSQS", "ReceiveMessage"));
                      attrs.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
                      attrs.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                      attrs.add(equalTo(URL_FULL, "http://localhost:" + sqsPort));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, sqsPort));
                      attrs.add(
                          equalTo(
                              MESSAGING_SYSTEM,
                              MessagingIncubatingAttributes.MessagingSystemIncubatingValues
                                  .AWS_SQS));
                      attrs.add(equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"));
                      attrs.add(equalTo(MESSAGING_OPERATION, "process"));
                      attrs.add(
                          satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)));
                      attrs.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
                      span.hasName("testSdkSqs process")
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(attrs);
                    },
                    span ->
                        span.hasName("process child")
                            .hasParent(trace.getSpan(1))
                            .hasAttributes(Attributes.empty())));
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

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
                      attrs.add(equalTo(stringKey("aws.queue.name"), "testSdkSqs"));
                      attrs.add(satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)));
                      attrs.add(rpcSystemAssertion("aws-api"));
                      attrs.addAll(rpcMethodAssertions("AmazonSQS", "CreateQueue"));
                      attrs.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
                      attrs.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                      attrs.add(equalTo(URL_FULL, "http://localhost:" + sqsPort));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, sqsPort));
                      attrs.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
                      span.hasName("SQS.CreateQueue")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attrs);
                    }),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
                      attrs.add(
                          equalTo(
                              AWS_SQS_QUEUE_URL,
                              "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"));
                      attrs.add(satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)));
                      attrs.add(rpcSystemAssertion("aws-api"));
                      attrs.addAll(rpcMethodAssertions("AmazonSQS", "SendMessage"));
                      attrs.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
                      attrs.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                      attrs.add(equalTo(URL_FULL, "http://localhost:" + sqsPort));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, sqsPort));
                      attrs.add(
                          equalTo(
                              MESSAGING_SYSTEM,
                              MessagingIncubatingAttributes.MessagingSystemIncubatingValues
                                  .AWS_SQS));
                      attrs.add(equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"));
                      attrs.add(equalTo(MESSAGING_OPERATION, "publish"));
                      attrs.add(
                          satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)));
                      attrs.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
                      span.hasName("testSdkSqs publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attrs);
                    },
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
                      attrs.add(
                          equalTo(
                              AWS_SQS_QUEUE_URL,
                              "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"));
                      attrs.add(satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)));
                      attrs.add(rpcSystemAssertion("aws-api"));
                      attrs.addAll(rpcMethodAssertions("AmazonSQS", "ReceiveMessage"));
                      attrs.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
                      attrs.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                      attrs.add(equalTo(URL_FULL, "http://localhost:" + sqsPort));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, sqsPort));
                      attrs.add(
                          equalTo(
                              MESSAGING_SYSTEM,
                              MessagingIncubatingAttributes.MessagingSystemIncubatingValues
                                  .AWS_SQS));
                      attrs.add(equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"));
                      attrs.add(equalTo(MESSAGING_OPERATION, "process"));
                      attrs.add(
                          satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)));
                      attrs.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
                      span.hasName("testSdkSqs process")
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(attrs);
                    },
                    span ->
                        span.hasName("process child")
                            .hasParent(trace.getSpan(1))
                            .hasAttributes(Attributes.empty())),
            /*
             * This span represents HTTP "sending of receive message" operation. It's always single, while there can be multiple CONSUMER spans (one per consumed message).
             * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation span would appear
             */
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
                      attrs.add(
                          equalTo(
                              AWS_SQS_QUEUE_URL,
                              "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"));
                      attrs.add(satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)));
                      attrs.add(rpcSystemAssertion("aws-api"));
                      attrs.addAll(rpcMethodAssertions("AmazonSQS", "ReceiveMessage"));
                      attrs.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
                      attrs.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                      attrs.add(equalTo(URL_FULL, "http://localhost:" + sqsPort));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, sqsPort));
                      attrs.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
                      span.hasName("SQS.ReceiveMessage")
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(attrs);
                    }));
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
    assertThat(receive.getAttributeNames()).isEqualTo(Collections.singletonList("AWSTraceHeader"));
  }
}
