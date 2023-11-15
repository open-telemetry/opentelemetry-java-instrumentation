/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SqsCamelTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final AwsConnector awsConnector = AwsConnector.elasticMq();

  @AfterAll
  static void cleanUp() {
    awsConnector.disconnect();
  }

  private static void waitAndClearSetupTraces(String queueUrl, String queueName) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, "SQS.CreateQueue", queueUrl, queueName)));
    testing.clearData();
  }

  @Test
  void camelSqsProducerToCamelSqsConsumer() {
    String queueName = "sqsCamelTest";
    String queueUrl = awsConnector.createQueue(queueName);
    waitAndClearSetupTraces(queueUrl, queueName);

    CamelSpringApplication camelApp =
        new CamelSpringApplication(
            awsConnector, SqsConfig.class, ImmutableMap.of("queueName", queueName));

    camelApp.start();
    camelApp.producerTemplate().sendBody("direct:input", "{\"type\": \"hello\"}");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, "SQS.ListQueues").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> CamelSpanAssertions.direct(span, "input"),
                span -> CamelSpanAssertions.sqsProduce(span, queueName).hasParent(trace.getSpan(0)),
                span ->
                    AwsSpanAssertions.sqs(
                            span, "sqsCamelTest publish", queueUrl, queueName, SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(1)),
                span ->
                    AwsSpanAssertions.sqs(
                            span, "sqsCamelTest process", queueUrl, queueName, SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(2)),
                span ->
                    CamelSpanAssertions.sqsConsume(span, queueName).hasParent(trace.getSpan(2))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, "SQS.DeleteMessage", queueUrl).hasNoParent()));
    camelApp.stop();
  }

  @Test
  void awsSdkSqsProducerToCamelSqsConsumer() {
    String queueName = "sqsCamelTest";
    String queueUrl = awsConnector.createQueue(queueName);
    waitAndClearSetupTraces(queueUrl, queueName);

    CamelSpringApplication camelApp =
        new CamelSpringApplication(
            awsConnector, SqsConfig.class, ImmutableMap.of("queueName", queueName));

    camelApp.start();
    awsConnector.sendSampleMessage(queueUrl);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, "SQS.ListQueues").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    AwsSpanAssertions.sqs(
                            span, "sqsCamelTest publish", queueUrl, queueName, SpanKind.PRODUCER)
                        .hasNoParent(),
                span ->
                    AwsSpanAssertions.sqs(
                            span, "sqsCamelTest process", queueUrl, queueName, SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0)),
                span ->
                    CamelSpanAssertions.sqsConsume(span, queueName).hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sqs(span, "SQS.DeleteMessage", queueUrl).hasNoParent()));
    camelApp.stop();
  }

  @Test
  void camelSqsProducerToAwsSdkSqsConsumer() {
    String queueName = "sqsCamelTestSdkConsumer";
    String queueUrl = awsConnector.createQueue(queueName);
    waitAndClearSetupTraces(queueUrl, queueName);

    CamelSpringApplication camelApp =
        new CamelSpringApplication(
            awsConnector, SqsConfig.class, ImmutableMap.of("queueSdkConsumerName", queueName));

    camelApp.start();
    camelApp.producerTemplate().sendBody("direct:inputSdkConsumer", "{\"type\": \"hello\"}");
    awsConnector.receiveMessage(queueUrl);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> AwsSpanAssertions.sqs(span, "SQS.ListQueues")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> CamelSpanAssertions.direct(span, "inputSdkConsumer"),
                span -> CamelSpanAssertions.sqsProduce(span, queueName).hasParent(trace.getSpan(0)),
                span ->
                    AwsSpanAssertions.sqs(
                            span,
                            "sqsCamelTestSdkConsumer publish",
                            queueUrl,
                            queueName,
                            SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(1)),
                span ->
                    AwsSpanAssertions.sqs(
                            span,
                            "sqsCamelTestSdkConsumer process",
                            queueUrl,
                            queueName,
                            SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(2))));
    camelApp.stop();
  }
}
