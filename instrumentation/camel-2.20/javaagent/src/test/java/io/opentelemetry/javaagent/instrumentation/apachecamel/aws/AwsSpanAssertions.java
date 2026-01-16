/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcMethodAssertions;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcSystemAssertion;
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
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_SQS_QUEUE_URL;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.util.ArrayList;
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

    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                satisfies(
                    stringKey("aws.queue.name"),
                    val ->
                        val.satisfiesAnyOf(
                            v -> assertThat(v).isEqualTo(queueName), v -> assertThat(v).isNull())),
                satisfies(
                    AWS_SQS_QUEUE_URL,
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
                rpcSystemAssertion("aws-api")));
    attributeAssertions.addAll(rpcMethodAssertions("AmazonSQS", rpcMethod));

    if (spanName.endsWith("receive")
        || spanName.endsWith("process")
        || spanName.endsWith("publish")) {
      attributeAssertions.addAll(
          asList(
              equalTo(MESSAGING_DESTINATION_NAME, queueName), equalTo(MESSAGING_SYSTEM, AWS_SQS)));
      if (spanName.endsWith("receive")) {
        attributeAssertions.add(equalTo(MESSAGING_OPERATION, "receive"));
      } else if (spanName.endsWith("process")) {
        attributeAssertions.add(equalTo(MESSAGING_OPERATION, "process"));
        attributeAssertions.add(satisfies(MESSAGING_MESSAGE_ID, val -> val.isNotNull()));
      } else if (spanName.endsWith("publish")) {
        attributeAssertions.add(equalTo(MESSAGING_OPERATION, "publish"));
        attributeAssertions.add(satisfies(MESSAGING_MESSAGE_ID, val -> val.isNotNull()));
      }
    }

    return span.hasName(spanName)
        .hasKind(spanKind)
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  static SpanDataAssert s3(SpanDataAssert span, String spanName, String bucketName, String method) {
    List<AttributeAssertion> attributeAssertions = new ArrayList<>();
    attributeAssertions.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
    attributeAssertions.add(equalTo(stringKey("aws.bucket.name"), bucketName));
    attributeAssertions.add(rpcSystemAssertion("aws-api"));
    attributeAssertions.addAll(rpcMethodAssertions("Amazon S3", spanName.substring(3)));
    attributeAssertions.add(equalTo(HTTP_REQUEST_METHOD, method));
    attributeAssertions.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
    attributeAssertions.add(satisfies(URL_FULL, val -> val.isInstanceOf(String.class)));
    attributeAssertions.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
    attributeAssertions.add(satisfies(SERVER_ADDRESS, val -> val.isInstanceOf(String.class)));
    attributeAssertions.add(
        satisfies(
            SERVER_PORT,
            val ->
                val.satisfiesAnyOf(
                    v -> val.isInstanceOf(Number.class), v -> assertThat(v).isNull())));

    return span.hasName(spanName).hasAttributesSatisfyingExactly(attributeAssertions);
  }

  static SpanDataAssert sns(
      SpanDataAssert span, String spanName, String topicArn, String destinationName) {
    List<AttributeAssertion> attributeAssertions = new ArrayList<>();
    attributeAssertions.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
    attributeAssertions.add(satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)));
    attributeAssertions.add(equalTo(AWS_SNS_TOPIC_ARN, topicArn));
    attributeAssertions.add(rpcSystemAssertion("aws-api"));
    attributeAssertions.addAll(rpcMethodAssertions("AmazonSNS", spanName.substring(4)));
    attributeAssertions.add(equalTo(MESSAGING_DESTINATION_NAME, destinationName));
    attributeAssertions.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
    attributeAssertions.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
    attributeAssertions.add(satisfies(URL_FULL, val -> val.isInstanceOf(String.class)));
    attributeAssertions.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
    attributeAssertions.add(satisfies(SERVER_ADDRESS, val -> val.isInstanceOf(String.class)));
    attributeAssertions.add(
        satisfies(
            SERVER_PORT,
            val ->
                val.satisfiesAnyOf(
                    v -> val.isInstanceOf(Number.class), v -> assertThat(v).isNull())));

    return span.hasName(spanName)
        .hasKind(CLIENT)
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }
}
