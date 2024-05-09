/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class AwsSpanAssertions {

  private AwsSpanAssertions() {}

  static SpanDataAssert sqs(SpanDataAssert span, String spanName) {
    return sqs(span, spanName, null, null, CLIENT);
  }

  static SpanDataAssert sqs(SpanDataAssert span, String spanName, String queueUrl) {
    return sqs(span, spanName, queueUrl, null, CLIENT);
  }

  static SpanDataAssert sqs(
      SpanDataAssert span, String spanName, String queueUrl, String queueName) {
    return sqs(span, spanName, queueUrl, queueName, CLIENT);
  }

  static SpanDataAssert sqs(
      SpanDataAssert span, String spanName, String queueUrl, String queueName, SpanKind spanKind) {

    String rpcMethod;
    if (spanName.startsWith("SQS.")) {
      rpcMethod = spanName.substring(4);
    } else if (spanName.endsWith("process")) {
      rpcMethod = "ReceiveMessage";
    } else if (spanName.endsWith("publish")) {
      rpcMethod = "SendMessage";
    } else {
      throw new IllegalStateException("can't get rpc method from span name " + spanName);
    }

    List<AttributeAssertion> attributeAssertions = new ArrayList<>();
    attributeAssertions.addAll(
        Arrays.asList(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            satisfies(stringKey("aws.endpoint"), val -> val.isInstanceOf(String.class)),
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
            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
            satisfies(UrlAttributes.URL_FULL, val -> val.isInstanceOf(String.class)),
            satisfies(
                ServerAttributes.SERVER_ADDRESS,
                stringAssert -> stringAssert.isInstanceOf(String.class)),
            satisfies(
                ServerAttributes.SERVER_PORT,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(),
                        v -> assertThat(v).isInstanceOf(Number.class))),
            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
            equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "aws-api"),
            satisfies(
                RpcIncubatingAttributes.RPC_METHOD,
                stringAssert -> stringAssert.isEqualTo(rpcMethod)),
            equalTo(RpcIncubatingAttributes.RPC_SERVICE, "AmazonSQS")));

    if (spanName.endsWith("receive")
        || spanName.endsWith("process")
        || spanName.endsWith("publish")) {
      attributeAssertions.addAll(
          Arrays.asList(
              equalTo(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, queueName),
              equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "AmazonSQS")));
      if (spanName.endsWith("receive")) {
        attributeAssertions.add(
            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "receive"));
      } else if (spanName.endsWith("process")) {
        attributeAssertions.add(
            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "process"));
        attributeAssertions.add(
            satisfies(
                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                val -> assertThat(val).isNotNull()));
      } else if (spanName.endsWith("publish")) {
        attributeAssertions.add(
            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish"));
        attributeAssertions.add(
            satisfies(
                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                val -> assertThat(val).isNotNull()));
      }
    }

    return span.hasName(spanName)
        .hasKind(spanKind)
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  static SpanDataAssert s3(SpanDataAssert span, String spanName, String bucketName, String method) {
    return span.hasName(spanName)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            satisfies(stringKey("aws.endpoint"), val -> val.isInstanceOf(String.class)),
            equalTo(stringKey("aws.bucket.name"), bucketName),
            equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "aws-api"),
            equalTo(RpcIncubatingAttributes.RPC_METHOD, spanName.substring(3)),
            equalTo(RpcIncubatingAttributes.RPC_SERVICE, "Amazon S3"),
            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, method),
            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
            satisfies(UrlAttributes.URL_FULL, val -> val.isInstanceOf(String.class)),
            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
            satisfies(ServerAttributes.SERVER_ADDRESS, val -> val.isInstanceOf(String.class)),
            satisfies(
                ServerAttributes.SERVER_PORT,
                val ->
                    val.satisfiesAnyOf(
                        v -> val.isInstanceOf(Number.class), v -> assertThat(v).isNull())));
  }

  static SpanDataAssert sns(SpanDataAssert span, String spanName, String topicArn) {
    return span.hasName(spanName)
        .hasKind(CLIENT)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            satisfies(stringKey("aws.endpoint"), val -> val.isInstanceOf(String.class)),
            equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "aws-api"),
            equalTo(RpcIncubatingAttributes.RPC_METHOD, spanName.substring(4)),
            equalTo(RpcIncubatingAttributes.RPC_SERVICE, "AmazonSNS"),
            equalTo(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, topicArn),
            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
            satisfies(UrlAttributes.URL_FULL, val -> val.isInstanceOf(String.class)),
            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
            satisfies(ServerAttributes.SERVER_ADDRESS, val -> val.isInstanceOf(String.class)),
            satisfies(
                ServerAttributes.SERVER_PORT,
                val ->
                    val.satisfiesAnyOf(
                        v -> val.isInstanceOf(Number.class), v -> assertThat(v).isNull())));
  }
}
