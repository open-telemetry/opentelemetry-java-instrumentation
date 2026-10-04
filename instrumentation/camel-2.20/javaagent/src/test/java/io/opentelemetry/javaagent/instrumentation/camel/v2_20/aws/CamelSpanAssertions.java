/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20.aws;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.ExperimentalTest.experimental;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.util.ArrayList;
import java.util.List;

class CamelSpanAssertions {

  private CamelSpanAssertions() {}

  static void direct(SpanDataAssert span, String spanName) {
    span.hasName(spanName)
        .hasKind(SpanKind.INTERNAL)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("camel.uri"), experimental("direct://" + spanName)));
  }

  static SpanDataAssert sqsProduce(SpanDataAssert span, String queueName) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(
                    stringKey("camel.uri"),
                    experimental(
                        "aws-sqs://" + queueName + "?amazonSQSClient=%23sqsClient&delay=1000")),
                equalTo(MESSAGING_SYSTEM, "aws_sqs"),
                equalTo(MESSAGING_DESTINATION_NAME, queueName),
                equalTo(MESSAGING_OPERATION_NAME, "send"),
                equalTo(MESSAGING_OPERATION_TYPE, "send")));
    attributeAssertions.add(satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)));

    return span.hasName("send " + queueName)
        .hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  static SpanDataAssert sqsConsume(SpanDataAssert span, String queueName) {
    return sqsConsume(span, queueName, 1000);
  }

  static SpanDataAssert sqsConsume(SpanDataAssert span, String queueName, int delay) {
    return span.hasName("process " + queueName)
        .hasKind(SpanKind.CONSUMER)
        .hasAttributesSatisfyingExactly(
            equalTo(
                stringKey("camel.uri"),
                experimental(
                    "aws-sqs://" + queueName + "?amazonSQSClient=%23sqsClient&delay=" + delay)),
            equalTo(MESSAGING_SYSTEM, "aws_sqs"),
            equalTo(MESSAGING_DESTINATION_NAME, queueName),
            equalTo(MESSAGING_OPERATION_NAME, "process"),
            equalTo(MESSAGING_OPERATION_TYPE, "process"),
            satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)));
  }

  static SpanDataAssert snsPublish(SpanDataAssert span, String topicName) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(
                    stringKey("camel.uri"),
                    experimental("aws-sns://" + topicName + "?amazonSNSClient=%23snsClient")),
                equalTo(MESSAGING_SYSTEM, "aws.sns"),
                equalTo(MESSAGING_DESTINATION_NAME, topicName),
                equalTo(MESSAGING_OPERATION_NAME, "send"),
                equalTo(MESSAGING_OPERATION_TYPE, "send")));
    attributeAssertions.add(satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)));

    return span.hasName("send " + topicName)
        .hasKind(SpanKind.PRODUCER)
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  static SpanDataAssert s3(SpanDataAssert span, String bucketName) {
    return span.hasName("aws-s3")
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfyingExactly(
            equalTo(
                stringKey("camel.uri"),
                experimental("aws-s3://" + bucketName + "?amazonS3Client=%23s3Client")));
  }
}
