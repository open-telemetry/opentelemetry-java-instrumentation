/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_SNS_TOPIC_ARN;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;

class AwsSpanAssertions {
  private AwsSpanAssertions() {}

  static SpanDataAssert sqs(
      SpanDataAssert span, String queueName, String queueUrl, String rpcMethod) {
    return span.hasName("SQS." + rpcMethod)
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(stringKey("aws.queue.name"), queueName),
            equalTo(stringKey("aws.queue.url"), queueUrl),
            satisfies(AWS_REQUEST_ID, v -> v.isInstanceOf(String.class)),
            equalTo(RPC_METHOD, rpcMethod),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "AmazonSQS"),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            satisfies(URL_FULL, val -> val.startsWith("http://")),
            satisfies(SERVER_ADDRESS, v -> v.isInstanceOf(String.class)),
            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
            satisfies(
                SERVER_PORT,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(),
                        v -> assertThat(v).isInstanceOf(Number.class))));
  }

  static SpanDataAssert s3(
      SpanDataAssert span,
      String bucketName,
      String rpcMethod,
      String requestMethod,
      int responseStatusCode) {

    return span.hasName("S3." + rpcMethod)
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(stringKey("aws.bucket.name"), bucketName),
            equalTo(RPC_METHOD, rpcMethod),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "Amazon S3"),
            equalTo(HTTP_REQUEST_METHOD, requestMethod),
            equalTo(HTTP_RESPONSE_STATUS_CODE, responseStatusCode),
            satisfies(URL_FULL, val -> val.startsWith("http://")),
            satisfies(SERVER_ADDRESS, v -> v.isInstanceOf(String.class)),
            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
            satisfies(
                SERVER_PORT,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(),
                        v -> assertThat(v).isInstanceOf(Number.class))));
  }

  static SpanDataAssert sns(SpanDataAssert span, String topicArn, String rpcMethod) {

    return span.hasName("SNS." + rpcMethod)
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(MESSAGING_DESTINATION_NAME, topicArn),
            satisfies(AWS_SNS_TOPIC_ARN, v -> v.isInstanceOf(String.class)),
            satisfies(AWS_REQUEST_ID, v -> v.isInstanceOf(String.class)),
            equalTo(RPC_METHOD, rpcMethod),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "AmazonSNS"),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            satisfies(URL_FULL, val -> val.startsWith("http://")),
            satisfies(SERVER_ADDRESS, v -> v.isInstanceOf(String.class)),
            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
            satisfies(
                SERVER_PORT,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(),
                        v -> assertThat(v).isInstanceOf(Number.class))));
  }
}
