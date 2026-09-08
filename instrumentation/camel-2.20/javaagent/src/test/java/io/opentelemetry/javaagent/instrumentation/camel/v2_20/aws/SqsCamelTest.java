/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20.aws;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelMessagingMetricsAssertions.assertProcessMetrics;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelMessagingMetricsAssertions.assertSendAndProcessMetrics;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelMessagingMetricsAssertions.assertSendMetrics;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.sqs.model.Message;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SqsCamelTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

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
                span -> {
                  CamelSpanAssertions.sqsConsume(span, queueName).hasParent(trace.getSpan(2));
                  if (emitStableMessagingSemconv()) {
                    span.hasLinks(propagatedLink(trace.getSpan(2)));
                  }
                }),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    AwsSpanAssertions.sqs(span, "SQS.DeleteMessage", queueUrl, queueName)
                        .hasNoParent()));
    assertSendAndProcessMetrics(testing, "aws_sqs", queueName);
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
                span -> {
                  CamelSpanAssertions.sqsConsume(span, queueName).hasParent(trace.getSpan(0));
                  if (emitStableMessagingSemconv()) {
                    span.hasLinks(propagatedLink(trace.getSpan(0)));
                  }
                }),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    AwsSpanAssertions.sqs(span, "SQS.DeleteMessage", queueUrl, queueName)
                        .hasNoParent()));
    assertProcessMetrics(testing, "aws_sqs", queueName);
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
    Message receivedMessage = awsConnector.receiveMessage(queueUrl);
    assertThat(receivedMessage.getAttributes()).containsKey("AWSTraceHeader");
    if (emitStableMessagingSemconv()) {
      assertThat(receivedMessage.getMessageAttributes()).doesNotContainKey("traceparent");
    } else {
      assertThat(receivedMessage.getMessageAttributes()).containsKey("traceparent");
    }

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
    assertSendMetrics(testing, "aws_sqs", queueName, null);
    camelApp.stop();
  }

  private static LinkData propagatedLink(SpanData producerSpan) {
    SpanContext producerContext = producerSpan.getSpanContext();
    return LinkData.create(
        SpanContext.create(
            producerContext.getTraceId(),
            producerContext.getSpanId(),
            TraceFlags.getSampled(),
            producerContext.getTraceState()));
  }
}
