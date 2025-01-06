/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.AwsSpanAssertions.sns;
import static io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.AwsSpanAssertions.sqs;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SnsTracingTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final AwsConnector awsConnector = new AwsConnector();

  @AfterAll
  static void cleanUp() {
    awsConnector.disconnect();
  }

  @Test
  @SuppressWarnings("deprecation") // MESSAGING_OPERATION is deprecated
  void testSnsNotificationTriggersSqsMessageConsumedWithAwsSdk() {
    String queueName = "snsToSqsTestQueue";
    String topicName = "snsToSqsTestTopic";

    String queueUrl = awsConnector.createQueue(queueName);
    String queueArn = awsConnector.getQueueArn(queueUrl);
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn);
    String topicArn = awsConnector.createTopicAndSubscribeQueue(topicName, queueArn);

    awsConnector.publishSampleNotification(topicArn);
    ReceiveMessageResult receiveMessageResult = awsConnector.receiveMessage(queueUrl);
    receiveMessageResult
        .getMessages()
        .forEach(message -> testing.runWithSpan("process child", () -> {}));

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> sqs(span, queueName, null, "CreateQueue")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> sqs(span, null, queueUrl, "GetQueueAttributes")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> sqs(span, null, queueUrl, "SetQueueAttributes")),
        trace -> trace.hasSpansSatisfyingExactly(span -> sns(span, null, "CreateTopic")),
        trace -> trace.hasSpansSatisfyingExactly(span -> sns(span, topicArn, "Subscribe")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> sns(span, topicArn, "Publish"),
                span ->
                    span.hasName("snsToSqsTestQueue process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                            satisfies(stringKey("aws.endpoint"), v -> v.isInstanceOf(String.class)),
                            equalTo(stringKey("aws.queue.url"), queueUrl),
                            satisfies(AWS_REQUEST_ID, v -> v.isInstanceOf(String.class)),
                            equalTo(RPC_METHOD, "ReceiveMessage"),
                            equalTo(RPC_SYSTEM, "aws-api"),
                            equalTo(RPC_SERVICE, "AmazonSQS"),
                            equalTo(HTTP_REQUEST_METHOD, "POST"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            satisfies(URL_FULL, val -> val.startsWith("http://")),
                            satisfies(SERVER_ADDRESS, v -> v.isInstanceOf(String.class)),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            satisfies(
                                SERVER_PORT,
                                val ->
                                    val.satisfiesAnyOf(
                                        v -> assertThat(v).isNull(),
                                        v -> assertThat(v).isInstanceOf(Number.class))),
                            equalTo(MESSAGING_SYSTEM, AWS_SQS),
                            equalTo(MESSAGING_DESTINATION_NAME, "snsToSqsTestQueue"),
                            equalTo(MESSAGING_OPERATION, "process"),
                            satisfies(MESSAGING_MESSAGE_ID, v -> v.isInstanceOf(String.class))),
                span ->
                    span.hasName("process child")
                        .hasParent(trace.getSpan(1))
                        .hasAttributes(Attributes.empty())));
  }
}
