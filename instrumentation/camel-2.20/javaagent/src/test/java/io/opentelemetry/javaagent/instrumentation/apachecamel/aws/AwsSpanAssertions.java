/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

class AwsSpanAssertions {

  private AwsSpanAssertions() {}

  static void sqs(SpanDataAssert span, int index, String spanName) {
    sqs(span, index, spanName, null, null, CLIENT, null);
  }

  static void sqs(SpanDataAssert span, int index, String spanName, String queueUrl) {
    sqs(span, index, spanName, queueUrl, null, CLIENT, null);
  }

  static void sqs(
      SpanDataAssert span, int index, String spanName, String queueUrl, String queueName) {
    sqs(span, index, spanName, queueUrl, queueName, CLIENT, null);
  }

  public static void sqs(
      SpanDataAssert span,
      int index,
      String spanName,
      String queueUrl,
      String queueName,
      SpanKind spanKind) {
    sqs(span, index, spanName, queueUrl, queueName, spanKind, null);
  }

  static void sqs(
      SpanDataAssert span,
      int index,
      String spanName,
      String queueUrl,
      String queueName,
      SpanKind spanKind,
      SpanData parentSpan) {
    if (index == 0) {
      span.hasNoParent();
    } else {
      span.hasParent(parentSpan);
    }

    span.hasName(spanName)
        .hasKind(spanKind)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            satisfies(stringKey("aws.endpoint"), val -> val.isInstanceOf(String.class)),
            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200),
            satisfies(
                stringKey("aws.queue.name"),
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isEqualTo(queueName), v -> assertThat(v).isNull())),
            satisfies(
                stringKey("aws.queue.url"),
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isEqualTo(queueUrl), v -> assertThat(v).isNull())),
            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
            satisfies(
                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(), v -> assertThat(v).isInstanceOf(Long.class))),
            satisfies(
                SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(), v -> assertThat(v).isInstanceOf(Long.class))),
            satisfies(
                SemanticAttributes.USER_AGENT_ORIGINAL,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(),
                        v -> assertThat(v).isInstanceOf(String.class))),
            satisfies(
                SemanticAttributes.HTTP_URL,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(),
                        v -> assertThat(v).isInstanceOf(String.class))),
            satisfies(
                stringKey("net.peer.name"),
                stringAssert -> stringAssert.isInstanceOf(String.class)),
            satisfies(
                longKey("net.peer.port"),
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(),
                        v -> assertThat(v).isInstanceOf(Number.class))),
            equalTo(stringKey("net.protocol.name"), "http"),
            equalTo(stringKey("net.protocol.version"), "1.1"),
            equalTo(stringKey("rpc.system"), "aws-api"),
            satisfies(
                stringKey("rpc.method"),
                stringAssert -> stringAssert.isEqualTo(spanName.substring(4))),
            equalTo(stringKey("rpc.service"), "AmazonSQS"));
  }

  static void s3(
      SpanDataAssert span,
      int index,
      String spanName,
      String bucketName,
      String method,
      SpanData parentSpan) {
    if (index == 0) {
      span.hasNoParent();
    } else {
      span.hasParent(parentSpan);
    }
    span.hasName(spanName)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            satisfies(stringKey("aws.endpoint"), val -> val.isInstanceOf(String.class)),
            equalTo(stringKey("rpc.system"), "aws-api"),
            equalTo(stringKey("rpc.method"), spanName.substring(3)),
            equalTo(stringKey("rpc.service"), "Amazon S3"),
            equalTo(stringKey("aws.bucket.name"), bucketName),
            equalTo(stringKey("http.method"), method),
            equalTo(longKey("http.status_code"), 200),
            satisfies(stringKey("http.url"), val -> val.isInstanceOf(String.class)),
            equalTo(stringKey("net.protocol.name"), "http"),
            equalTo(stringKey("net.protocol.version"), "1.1"),
            satisfies(stringKey("net.peer.name"), val -> val.isInstanceOf(String.class)),
            satisfies(
                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(), v -> assertThat(v).isInstanceOf(Long.class))),
            satisfies(
                stringKey("net.peer.port"),
                val ->
                    val.satisfiesAnyOf(
                        v -> val.isInstanceOf(Number.class), v -> assertThat(v).isNull())));
  }

  static void sns(SpanDataAssert span, int index, String spanName, SpanData parentSpan) {
    if (index == 0) {
      span.hasNoParent();
    } else {
      span.hasParent(parentSpan);
    }
    span.hasName(spanName)
        .hasKind(CLIENT)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            satisfies(stringKey("aws.endpoint"), val -> val.isInstanceOf(String.class)),
            equalTo(stringKey("rpc.system"), "aws-api"),
            equalTo(stringKey("rpc.method"), spanName.substring(4)),
            equalTo(stringKey("rpc.service"), "AmazonSNS"),
            equalTo(stringKey("http.method"), "POST"),
            equalTo(longKey("http.status_code"), 200),
            satisfies(stringKey("http.url"), val -> val.isInstanceOf(String.class)),
            equalTo(stringKey("net.protocol.name"), "http"),
            equalTo(stringKey("net.protocol.version"), "1.1"),
            satisfies(stringKey("net.peer.name"), val -> val.isInstanceOf(String.class)),
            satisfies(
                stringKey("net.peer.port"),
                val ->
                    val.satisfiesAnyOf(
                        v -> val.isInstanceOf(Number.class), v -> assertThat(v).isNull())),
            satisfies(
                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(), v -> assertThat(v).isInstanceOf(Long.class))));
  }
}
