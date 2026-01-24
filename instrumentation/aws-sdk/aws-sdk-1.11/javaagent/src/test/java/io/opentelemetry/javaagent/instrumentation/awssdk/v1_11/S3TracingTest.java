/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcMethodAssertions;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcSystemAssertion;
import static io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.AwsSpanAssertions.s3;
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
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_SQS_QUEUE_URL;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // MESSAGING_OPERATION is deprecated
class S3TracingTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final AwsConnector awsConnector = new AwsConnector();

  @AfterAll
  static void cleanUp() {
    awsConnector.disconnect();
  }

  @Test
  void testS3UploadTriggersSqsMessage() {
    String queueName = "s3ToSqsTestQueue";
    String bucketName = "otel-s3-to-sqs-test-bucket";

    String queueUrl = awsConnector.createQueue(queueName);
    awsConnector.createBucket(bucketName);

    String queueArn = awsConnector.getQueueArn(queueUrl);
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn);
    awsConnector.enableS3ToSqsNotifications(bucketName, queueArn);

    // test message, auto created by AWS
    awsConnector.receiveMessage(queueUrl);
    awsConnector.putSampleData(bucketName);

    // traced message
    ReceiveMessageResult receiveMessageResult = awsConnector.receiveMessage(queueUrl);
    receiveMessageResult
        .getMessages()
        .forEach(message -> testing.runWithSpan("process child", () -> {}));

    // cleanup
    awsConnector.deleteBucket(bucketName);
    awsConnector.purgeQueue(queueUrl);

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> sqs(span, queueName, null, "CreateQueue")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> s3(span, bucketName, "CreateBucket", "PUT", 200)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> sqs(span, null, queueUrl, "GetQueueAttributes")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> sqs(span, null, queueUrl, "SetQueueAttributes")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> s3(span, bucketName, "SetBucketNotificationConfiguration", "PUT", 200)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> s3(span, bucketName, "PutObject", "PUT", 200),
                span -> {
                  List<AttributeAssertion> attributes = new ArrayList<>();
                  attributes.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
                  attributes.add(equalTo(AWS_SQS_QUEUE_URL, queueUrl));
                  attributes.add(satisfies(AWS_REQUEST_ID, v -> v.isInstanceOf(String.class)));
                  attributes.add(rpcSystemAssertion("aws-api"));
                  attributes.addAll(rpcMethodAssertions("AmazonSQS", "ReceiveMessage"));
                  attributes.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
                  attributes.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                  attributes.add(satisfies(URL_FULL, val -> val.startsWith("http://")));
                  attributes.add(satisfies(SERVER_ADDRESS, v -> v.isInstanceOf(String.class)));
                  attributes.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
                  attributes.add(
                      satisfies(
                          SERVER_PORT,
                          val ->
                              val.satisfiesAnyOf(
                                  v -> assertThat(v).isNull(),
                                  v -> assertThat(v).isInstanceOf(Number.class))));
                  attributes.add(equalTo(MESSAGING_SYSTEM, AWS_SQS));
                  attributes.add(equalTo(MESSAGING_DESTINATION_NAME, "s3ToSqsTestQueue"));
                  attributes.add(equalTo(MESSAGING_OPERATION, "process"));
                  attributes.add(
                      satisfies(MESSAGING_MESSAGE_ID, v -> v.isInstanceOf(String.class)));

                  span.hasName("s3ToSqsTestQueue process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(attributes);
                },
                span ->
                    span.hasName("process child")
                        .hasParent(trace.getSpan(1))
                        .hasAttributes(Attributes.empty())),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> s3(span, bucketName, "ListObjects", "GET", 200)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> s3(span, bucketName, "DeleteObject", "DELETE", 204)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> s3(span, bucketName, "DeleteBucket", "DELETE", 204)),
        trace -> trace.hasSpansSatisfyingExactly(span -> sqs(span, null, queueUrl, "PurgeQueue")));
  }

  @Test
  void testS3UploadTriggersSnsTopicNotificationThenCreatesSqsMessage() {
    String queueName = "s3ToSnsToSqsTestQueue";
    String bucketName = "otel-s3-to-sns-to-sqs-test-bucket";
    String topicName = "s3ToSnsTestTopic";

    String queueUrl = awsConnector.createQueue(queueName);
    String queueArn = awsConnector.getQueueArn(queueUrl);
    awsConnector.createBucket(bucketName);
    String topicArn = awsConnector.createTopicAndSubscribeQueue(topicName, queueArn);

    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn);
    awsConnector.setTopicPublishingPolicy(topicArn);
    awsConnector.enableS3ToSnsNotifications(bucketName, topicArn);

    // test message, auto created by AWS
    awsConnector.receiveMessage(queueUrl);
    awsConnector.putSampleData(bucketName);

    // traced message
    ReceiveMessageResult receiveMessageResult = awsConnector.receiveMessage(queueUrl);
    receiveMessageResult
        .getMessages()
        .forEach(message -> testing.runWithSpan("process child", () -> {}));

    // cleanup
    awsConnector.deleteBucket(bucketName);
    awsConnector.purgeQueue(queueUrl);

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> sqs(span, queueName, null, "CreateQueue")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> sqs(span, null, queueUrl, "GetQueueAttributes")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> s3(span, bucketName, "CreateBucket", "PUT", 200)),
        trace -> trace.hasSpansSatisfyingExactly(span -> sns(span, null, "CreateTopic")),
        trace -> trace.hasSpansSatisfyingExactly(span -> sns(span, topicArn, "Subscribe")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> sqs(span, null, queueUrl, "SetQueueAttributes")),
        trace -> trace.hasSpansSatisfyingExactly(span -> sns(span, topicArn, "SetTopicAttributes")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> s3(span, bucketName, "SetBucketNotificationConfiguration", "PUT", 200)),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> s3(span, bucketName, "PutObject", "PUT", 200)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  List<AttributeAssertion> attributes = new ArrayList<>();
                  attributes.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
                  attributes.add(equalTo(AWS_SQS_QUEUE_URL, queueUrl));
                  attributes.add(satisfies(AWS_REQUEST_ID, v -> v.isInstanceOf(String.class)));
                  attributes.add(rpcSystemAssertion("aws-api"));
                  attributes.addAll(rpcMethodAssertions("AmazonSQS", "ReceiveMessage"));
                  attributes.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
                  attributes.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                  attributes.add(satisfies(URL_FULL, val -> val.startsWith("http://")));
                  attributes.add(satisfies(SERVER_ADDRESS, v -> v.isInstanceOf(String.class)));
                  attributes.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
                  attributes.add(
                      satisfies(
                          SERVER_PORT,
                          val ->
                              val.satisfiesAnyOf(
                                  v -> assertThat(v).isNull(),
                                  v -> assertThat(v).isInstanceOf(Number.class))));
                  attributes.add(equalTo(MESSAGING_SYSTEM, AWS_SQS));
                  attributes.add(equalTo(MESSAGING_DESTINATION_NAME, "s3ToSnsToSqsTestQueue"));
                  attributes.add(equalTo(MESSAGING_OPERATION, "process"));
                  attributes.add(
                      satisfies(MESSAGING_MESSAGE_ID, v -> v.isInstanceOf(String.class)));

                  span.hasName("s3ToSnsToSqsTestQueue process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(attributes);
                },
                span ->
                    span.hasName("process child")
                        .hasParent(trace.getSpan(0))
                        .hasAttributes(Attributes.empty())),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> s3(span, bucketName, "ListObjects", "GET", 200)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> s3(span, bucketName, "DeleteObject", "DELETE", 204)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> s3(span, bucketName, "DeleteBucket", "DELETE", 204)),
        trace -> trace.hasSpansSatisfyingExactly(span -> sqs(span, null, queueUrl, "PurgeQueue")));
  }
}
