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
import java.util.HashMap;
import java.util.Map;
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

  private static final AwsConnector awsConnector = AwsConnector.liveAws();

  @Test
  void awsSdkSnsProducerToCamelSqsConsumer() {
    String topicName = "snsCamelTest";
    String queueName = "snsCamelTest";

    CamelSpringApplication camelApp =
        new CamelSpringApplication(
            awsConnector,
            SnsConfig.class,
            ImmutableMap.of("topicName", topicName, "queueName", queueName));

    Map<String, String> metaData = setupTestInfrastructure(queueName, topicName);
    String queueUrl = metaData.get("queueUrl");
    String topicArn = metaData.get("topicArn");
    waitAndClearSetupTraces(queueUrl, queueName);

    camelApp.start();
    awsConnector.publishSampleNotification(topicArn);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.ListQueues")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sns(span, 0, "SNS.ListTopics", null)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sns(span, 0, "SNS.Publish", null),
                span ->
                    AwsSpanAssertions.sqs(
                        span,
                        1,
                        "SQS.ReceiveMessage",
                        queueUrl,
                        null,
                        SpanKind.CONSUMER,
                        trace.getSpan(0)),
                span -> CamelSpanAssertions.sqsConsume(span, 2, queueName, trace.getSpan(0))),
        // http client span
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.DeleteMessage", queueUrl)),
        // camel polling
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl)));

    try {
      awsConnector.purgeQueue(queueUrl);
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

    Map<String, String> metaData = setupTestInfrastructure(queueName, topicName);
    String queueUrl = metaData.get("queueUrl");
    waitAndClearSetupTraces(queueUrl, queueName);

    camelApp.start();
    camelApp.producerTemplate().sendBody("direct:input", "{\"type\": \"hello\"}");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.ListQueues")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sns(span, 0, "SNS.ListTopics", null)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> CamelSpanAssertions.direct(span, "input"),
                span -> CamelSpanAssertions.snsPublish(span, 1, topicName, trace.getSpan(0)),
                span -> AwsSpanAssertions.sns(span, 2, "SNS.Publish", trace.getSpan(1)),
                span ->
                    AwsSpanAssertions.sqs(
                        span,
                        3,
                        "SQS.ReceiveMessage",
                        queueUrl,
                        null,
                        SpanKind.CONSUMER,
                        trace.getSpan(2)),
                span -> CamelSpanAssertions.sqsConsume(span, 4, queueName, trace.getSpan(2))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.DeleteMessage", queueUrl)),
        // camel polling
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl)));

    try {
      awsConnector.purgeQueue(queueUrl);
    } catch (PurgeQueueInProgressException e) {
      logger.warn("Throttled by AWS trying to purge queue, try doing it manually.");
    }
    camelApp.stop();
  }

  private static void waitAndClearSetupTraces(String queueUrl, String queueName) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.CreateQueue", queueUrl, queueName)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.GetQueueAttributes", queueUrl)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.SetQueueAttributes", queueUrl)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sns(span, 0, "SNS.CreateTopic", null)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sns(span, 0, "SNS.Subscribe", null)),
        // test message
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl)));
    testing.clearData();
  }

  Map<String, String> setupTestInfrastructure(String queueName, String topicName) {
    // setup infra
    String queueUrl = awsConnector.createQueue(queueName);
    String queueArn = awsConnector.getQueueArn(queueUrl);
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn);
    String topicArn = awsConnector.createTopicAndSubscribeQueue(topicName, queueArn);

    // consume test message from AWS
    awsConnector.receiveMessage(queueUrl);

    Map<String, String> metaData = new HashMap<>();
    metaData.put("queueUrl", queueUrl);
    metaData.put("topicArn", topicArn);
    return metaData;
  }
}
