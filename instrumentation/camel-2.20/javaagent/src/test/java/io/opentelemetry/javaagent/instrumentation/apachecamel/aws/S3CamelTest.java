/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;

import com.amazonaws.services.sqs.model.PurgeQueueInProgressException;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled("Does not work with localstack - X-Ray features needed")
class S3CamelTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger(S3CamelTest.class);

  // To account for any delay interacting with real AWS environment
  private static final int sqsDelay = 10000;

  private static AwsConnector awsConnector;

  @BeforeAll
  protected static void setUp() {
    awsConnector = AwsConnector.liveAws();
  }

  @Test
  public void camelS3ProducerToCamelSqsConsumer() {
    String queueName = "s3SqsCamelTest";
    String bucketName = "bucket-test-s3-sqs-camel";

    CamelSpringApplication camelApp =
        new CamelSpringApplication(
            awsConnector,
            S3Config.class,
            ImmutableMap.of("bucketName", bucketName, "queueName", queueName));

    String queueUrl = setupTestInfrastructure(queueName, bucketName);
    waitAndClearSetupTraces(queueUrl, queueName, bucketName);

    camelApp.start();
    camelApp.producerTemplate().sendBody("direct:input", "{\"type\": \"hello\"}");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, "SQS.ListQueues").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    AwsSpanAssertions.s3(span, "S3.ListObjects", bucketName, "GET").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> CamelSpanAssertions.direct(span, "input"),
                span -> CamelSpanAssertions.s3(span, bucketName).hasParent(trace.getSpan(0)),
                span ->
                    AwsSpanAssertions.s3(span, "S3.PutObject", bucketName, "PUT")
                        .hasParent(trace.getSpan(1)),
                span ->
                    AwsSpanAssertions.sqs(span, "SQS.ReceiveMessage", queueUrl, null, CONSUMER)
                        .hasParent(trace.getSpan(2)),
                span ->
                    CamelSpanAssertions.sqsConsume(span, queueName, sqsDelay)
                        .hasParent(trace.getSpan(2))),
        // HTTP "client" receiver span, one per each SQS request
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    AwsSpanAssertions.sqs(span, "SQS.ReceiveMessage", queueUrl, null, CLIENT)
                        .hasNoParent()),
        // camel cleaning received msg
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, "SQS.DeleteMessage", queueUrl).hasNoParent()));

    camelApp.stop();
    awsConnector.deleteBucket(bucketName);
    try {
      awsConnector.purgeQueue(queueUrl);
    } catch (PurgeQueueInProgressException e) {
      logger.warn("Throttled by AWS trying to purge queue, try doing it manually.");
    }
  }

  String setupTestInfrastructure(String queueName, String bucketName) {
    // setup infra
    String queueUrl = awsConnector.createQueue(queueName);
    awsConnector.createBucket(bucketName);
    String queueArn = awsConnector.getQueueArn(queueUrl);
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn);
    awsConnector.enableS3ToSqsNotifications(bucketName, queueArn);

    // consume test message from AWS
    awsConnector.receiveMessage(queueUrl);

    return queueUrl;
  }

  private static void waitAndClearSetupTraces(
      String queueUrl, String queueName, String bucketName) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    AwsSpanAssertions.sqs(span, "SQS.CreateQueue", queueUrl, queueName)
                        .hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    AwsSpanAssertions.s3(span, "S3.CreateBucket", bucketName, "PUT").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    AwsSpanAssertions.sqs(span, "SQS.GetQueueAttributes", queueUrl).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    AwsSpanAssertions.sqs(span, "SQS.SetQueueAttributes", queueUrl).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    AwsSpanAssertions.s3(
                            span, "S3.SetBucketNotificationConfiguration", bucketName, "PUT")
                        .hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, "SQS.ReceiveMessage", queueUrl).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    AwsSpanAssertions.sqs(span, "SQS.ReceiveMessage", queueUrl, null, CONSUMER)
                        .hasNoParent()));
    testing.clearData();
  }
}
