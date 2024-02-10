/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import com.amazonaws.services.sqs.model.PurgeQueueInProgressException;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled("Does not work with localstack - X-Ray features needed")
class SnsCamelTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger(SnsCamelTest.class);

  private static AwsConnector awsConnector;

  @BeforeAll
  protected static void setUp() {
    awsConnector = AwsConnector.liveAws();
  }

  @Test
  void awsSdkSnsProducerToCamelSqsConsumer() {
    String topicName = "snsCamelTest";
    String queueName = "snsCamelTest";

    CamelSpringApplication camelApp =
        new CamelSpringApplication(
            awsConnector,
            SnsConfig.class,
            ImmutableMap.of("topicName", topicName, "queueName", queueName));

    SnsMetadata metaData = setupTestInfrastructure(queueName, topicName);
    waitAndClearSetupTraces(metaData.queueUrl, queueName, metaData.topicArn);

    camelApp.start();
    awsConnector.publishSampleNotification(metaData.topicArn);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, "SQS.ListQueues").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sns(span, "SNS.ListTopics", null).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sns(span, "SNS.Publish", metaData.topicArn).hasNoParent(),
                span ->
                    AwsSpanAssertions.sqs(
                            span,
                            "snsCamelTest process",
                            metaData.queueUrl,
                            queueName,
                            SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0)),
                span ->
                    CamelSpanAssertions.sqsConsume(span, queueName).hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    AwsSpanAssertions.sqs(span, "SQS.DeleteMessage", metaData.queueUrl)
                        .hasNoParent()));

    try {
      awsConnector.purgeQueue(metaData.queueUrl);
    } catch (PurgeQueueInProgressException e) {
      logger.warn("Throttled by AWS trying to purge queue, try doing it manually.");
    }
    camelApp.stop();
  }

  @Test
  void camelSnsProducerToCamelSqsConsumer() {
    String topicName = "snsCamelTest";
    String queueName = "snsCamelTest";

    CamelSpringApplication camelApp =
        new CamelSpringApplication(
            awsConnector,
            SnsConfig.class,
            ImmutableMap.of("topicName", topicName, "queueName", queueName));

    SnsMetadata metaData = setupTestInfrastructure(queueName, topicName);
    String queueUrl = metaData.queueUrl;
    waitAndClearSetupTraces(queueUrl, queueName, metaData.topicArn);

    camelApp.start();
    camelApp.producerTemplate().sendBody("direct:input", "{\"type\": \"hello\"}");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, "SQS.ListQueues").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sns(span, "SNS.ListTopics", null).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> CamelSpanAssertions.direct(span, "input"),
                span -> CamelSpanAssertions.snsPublish(span, topicName).hasParent(trace.getSpan(0)),
                span ->
                    AwsSpanAssertions.sns(span, "SNS.Publish", metaData.topicArn)
                        .hasParent(trace.getSpan(1)),
                span ->
                    AwsSpanAssertions.sqs(
                            span, "snsCamelTest process", queueUrl, queueName, SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(2)),
                span ->
                    CamelSpanAssertions.sqsConsume(span, queueName).hasParent(trace.getSpan(2))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, "SQS.DeleteMessage", queueUrl).hasNoParent()));

    try {
      awsConnector.purgeQueue(queueUrl);
    } catch (PurgeQueueInProgressException e) {
      logger.warn("Throttled by AWS trying to purge queue, try doing it manually.");
    }
    camelApp.stop();
  }

  private static void waitAndClearSetupTraces(String queueUrl, String queueName, String topicArn) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    AwsSpanAssertions.sqs(span, "SQS.CreateQueue", queueUrl, queueName)
                        .hasNoParent()),
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
                span -> AwsSpanAssertions.sns(span, "SNS.CreateTopic", null).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sns(span, "SNS.Subscribe", topicArn).hasNoParent()));
    testing.clearData();
  }

  SnsMetadata setupTestInfrastructure(String queueName, String topicName) {
    // setup infra
    String queueUrl = awsConnector.createQueue(queueName);
    String queueArn = awsConnector.getQueueArn(queueUrl);
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn);
    String topicArn = awsConnector.createTopicAndSubscribeQueue(topicName, queueArn);

    // consume test message from AWS
    awsConnector.receiveMessage(queueUrl);

    return new SnsMetadata(queueUrl, topicArn);
  }

  private static final class SnsMetadata {
    private final String queueUrl;
    private final String topicArn;

    public SnsMetadata(String queueUrl, String topicArn) {
      this.queueUrl = queueUrl;
      this.topicArn = topicArn;
    }
  }
}
