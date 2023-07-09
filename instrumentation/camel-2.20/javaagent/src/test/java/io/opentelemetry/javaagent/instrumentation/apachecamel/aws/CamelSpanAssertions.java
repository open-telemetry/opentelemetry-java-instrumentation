/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

class CamelSpanAssertions {

  private CamelSpanAssertions() {}

  static void direct(SpanDataAssert span, String spanName) {
    span.hasName(spanName)
        .hasKind(SpanKind.INTERNAL)
        .hasNoParent()
        .hasAttribute(stringKey("camel.uri"), "direct://" + spanName);
  }

  static void sqsProduce(SpanDataAssert span, int index, String queueName, SpanData parentSpan) {
    if (index == 0) {
      span.hasNoParent();
    } else {
      span.hasParentSpanId(parentSpan.getSpanId());
    }
    span.hasName(queueName)
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfyingExactly(
            equalTo(
                stringKey("camel.uri"),
                "aws-sqs://" + queueName + "?amazonSQSClient=%23sqsClient&delay=1000"),
            equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, queueName));
  }

  static void sqsConsume(SpanDataAssert span, int index, String queueName, SpanData parentSpan) {
    if (index == 0) {
      span.hasNoParent();
    } else {
      span.hasParentSpanId(parentSpan.getSpanId());
    }
    span.hasName(queueName)
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfying(
            equalTo(
                stringKey("camel.uri"),
                "aws-sqs://" + queueName + "?amazonSQSClient=%23sqsClient&delay=1000"),
            equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, queueName),
            satisfies(
                SemanticAttributes.MESSAGING_MESSAGE_ID,
                stringAssert -> stringAssert.isInstanceOf(String.class)));
  }
}
