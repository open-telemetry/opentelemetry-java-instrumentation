/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
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

  @SuppressWarnings("deprecation") // using deprecated semconv
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
            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            satisfies(URL_FULL, val -> val.isInstanceOf(String.class)),
            satisfies(SERVER_ADDRESS, stringAssert -> stringAssert.isInstanceOf(String.class)),
            satisfies(
                SERVER_PORT,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(),
                        v -> assertThat(v).isInstanceOf(Number.class))),
            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
            equalTo(RPC_SYSTEM, "aws-api"),
            satisfies(RPC_METHOD, stringAssert -> stringAssert.isEqualTo(rpcMethod)),
            equalTo(RPC_SERVICE, "AmazonSQS")));

    if (spanName.endsWith("receive")
        || spanName.endsWith("process")
        || spanName.endsWith("publish")) {
      attributeAssertions.addAll(
          Arrays.asList(
              equalTo(MESSAGING_DESTINATION_NAME, queueName), equalTo(MESSAGING_SYSTEM, AWS_SQS)));
      if (spanName.endsWith("receive")) {
        attributeAssertions.add(equalTo(MESSAGING_OPERATION, "receive"));
      } else if (spanName.endsWith("process")) {
        attributeAssertions.add(equalTo(MESSAGING_OPERATION, "process"));
        attributeAssertions.add(
            satisfies(MESSAGING_MESSAGE_ID, val -> assertThat(val).isNotNull()));
      } else if (spanName.endsWith("publish")) {
        attributeAssertions.add(equalTo(MESSAGING_OPERATION, "publish"));
        attributeAssertions.add(
            satisfies(MESSAGING_MESSAGE_ID, val -> assertThat(val).isNotNull()));
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
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_METHOD, spanName.substring(3)),
            equalTo(RPC_SERVICE, "Amazon S3"),
            equalTo(HTTP_REQUEST_METHOD, method),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            satisfies(URL_FULL, val -> val.isInstanceOf(String.class)),
            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
            satisfies(SERVER_ADDRESS, val -> val.isInstanceOf(String.class)),
            satisfies(
                SERVER_PORT,
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
            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_METHOD, spanName.substring(4)),
            equalTo(RPC_SERVICE, "AmazonSNS"),
            equalTo(MESSAGING_DESTINATION_NAME, topicArn),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            satisfies(URL_FULL, val -> val.isInstanceOf(String.class)),
            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
            satisfies(SERVER_ADDRESS, val -> val.isInstanceOf(String.class)),
            satisfies(
                SERVER_PORT,
                val ->
                    val.satisfiesAnyOf(
                        v -> val.isInstanceOf(Number.class), v -> assertThat(v).isNull())));
  }
}
