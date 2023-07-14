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
                span -> AwsSpanAssertions.sqs(span, 0, "SQS.CreateQueue", queueUrl, queueName)));
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
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span -> AwsSpanAssertions.sqs(span, 0, "SQS.ListQueues")),
        trace ->
            trace
                .hasSize(5)
                .hasSpansSatisfyingExactly(
                    span -> CamelSpanAssertions.direct(span, "input"),
                    span -> CamelSpanAssertions.sqsProduce(span, 1, queueName, trace.getSpan(0)),
                    span ->
                        AwsSpanAssertions.sqs(
                            span,
                            2,
                            "SQS.SendMessage",
                            queueUrl,
                            null,
                            SpanKind.PRODUCER,
                            trace.getSpan(1)),
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
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl)),
        trace ->
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span -> AwsSpanAssertions.sqs(span, 0, "SQS.DeleteMessage", queueUrl)));
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
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span -> AwsSpanAssertions.sqs(span, 0, "SQS.ListQueues")),
        trace ->
            trace
                .hasSize(3)
                .hasSpansSatisfyingExactly(
                    span ->
                        AwsSpanAssertions.sqs(
                            span, 0, "SQS.SendMessage", queueUrl, null, SpanKind.PRODUCER),
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
        trace ->
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl)),
        trace ->
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span -> AwsSpanAssertions.sqs(span, 0, "SQS.DeleteMessage", queueUrl)),
        trace ->
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl)));
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
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span -> AwsSpanAssertions.sqs(span, 0, "SQS.ListQueues")),
        trace ->
            trace
                .hasSize(4)
                .hasSpansSatisfyingExactly(
                    span -> CamelSpanAssertions.direct(span, "inputSdkConsumer"),
                    span -> CamelSpanAssertions.sqsProduce(span, 1, queueName, trace.getSpan(0)),
                    span ->
                        AwsSpanAssertions.sqs(
                            span,
                            2,
                            "SQS.SendMessage",
                            queueUrl,
                            null,
                            SpanKind.PRODUCER,
                            trace.getSpan(1)),
                    span ->
                        AwsSpanAssertions.sqs(
                            span,
                            3,
                            "SQS.ReceiveMessage",
                            queueUrl,
                            null,
                            SpanKind.CONSUMER,
                            trace.getSpan(2))),
        /*
         * This span represents HTTP "sending of receive message" operation. It's always single, while there can be multiple CONSUMER spans (one per consumed message).
         * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation span would appear
         */
        trace ->
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span -> AwsSpanAssertions.sqs(span, 0, "SQS.ReceiveMessage", queueUrl)));
    camelApp.stop();
  }
}
