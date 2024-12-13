/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;

class CamelSpanAssertions {

  private CamelSpanAssertions() {}

  static void direct(SpanDataAssert span, String spanName) {
    span.hasName(spanName)
        .hasKind(SpanKind.INTERNAL)
        .hasNoParent()
        .hasAttribute(stringKey("camel.uri"), "direct://" + spanName);
  }

  static SpanDataAssert sqsProduce(SpanDataAssert span, String queueName) {
    return span.hasName(queueName)
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfyingExactly(
            equalTo(
                stringKey("camel.uri"),
                "aws-sqs://" + queueName + "?amazonSQSClient=%23sqsClient&delay=1000"),
            equalTo(MESSAGING_DESTINATION_NAME, queueName));
  }

  static SpanDataAssert sqsConsume(SpanDataAssert span, String queueName) {
    return sqsConsume(span, queueName, 1000);
  }

  static SpanDataAssert sqsConsume(SpanDataAssert span, String queueName, int delay) {
    return span.hasName(queueName)
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfying(
            equalTo(
                stringKey("camel.uri"),
                "aws-sqs://" + queueName + "?amazonSQSClient=%23sqsClient&delay=" + delay),
            equalTo(MESSAGING_DESTINATION_NAME, queueName),
            satisfies(
                MESSAGING_MESSAGE_ID, stringAssert -> stringAssert.isInstanceOf(String.class)));
  }

  static SpanDataAssert snsPublish(SpanDataAssert span, String topicName) {
    return span.hasName(topicName)
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfying(
            equalTo(
                stringKey("camel.uri"), "aws-sns://" + topicName + "?amazonSNSClient=%23snsClient"),
            equalTo(MESSAGING_DESTINATION_NAME, topicName));
  }

  static SpanDataAssert s3(SpanDataAssert span, String bucketName) {
    return span.hasName("aws-s3")
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfying(
            equalTo(
                stringKey("camel.uri"), "aws-s3://" + bucketName + "?amazonS3Client=%23s3Client"));
  }
}
