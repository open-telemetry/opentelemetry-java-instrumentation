/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
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
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_S3_BUCKET;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_SNS_TOPIC_ARN;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_SQS_QUEUE_URL;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.util.ArrayList;
import java.util.List;

class AwsSpanAssertions {
  private AwsSpanAssertions() {}

  @SuppressWarnings("deprecation") // using deprecated semconv
  static SpanDataAssert sqs(
      SpanDataAssert span, String queueName, String queueUrl, String rpcMethod) {
    List<AttributeAssertion> attributes = new ArrayList<>();
    attributes.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
    if (queueName != null) {
      attributes.add(equalTo(stringKey("aws.queue.name"), queueName));
    }
    if (queueUrl != null) {
      attributes.add(equalTo(AWS_SQS_QUEUE_URL, queueUrl));
    }
    attributes.add(satisfies(AWS_REQUEST_ID, v -> v.isInstanceOf(String.class)));
    attributes.add(rpcSystemAssertion("aws-api"));
    attributes.addAll(rpcMethodAssertions("AmazonSQS", rpcMethod));
    attributes.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
    attributes.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
    attributes.add(satisfies(URL_FULL, val -> val.startsWith("http://")));
    attributes.add(satisfies(SERVER_ADDRESS, v -> v.isInstanceOf(String.class)));
    attributes.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
    attributes.add(
        satisfies(
            SERVER_PORT,
            val ->
                val.satisfiesAnyOf(
                    v -> assertThat(v).isNull(), v -> assertThat(v).isInstanceOf(Number.class))));

    return span.hasName("SQS." + rpcMethod)
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(attributes);
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  static SpanDataAssert s3(
      SpanDataAssert span,
      String bucketName,
      String rpcMethod,
      String requestMethod,
      int responseStatusCode) {
    List<AttributeAssertion> attributes = new ArrayList<>();
    attributes.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
    attributes.add(equalTo(AWS_S3_BUCKET, bucketName));
    attributes.add(rpcSystemAssertion("aws-api"));
    attributes.addAll(rpcMethodAssertions("Amazon S3", rpcMethod));
    attributes.add(equalTo(HTTP_REQUEST_METHOD, requestMethod));
    attributes.add(equalTo(HTTP_RESPONSE_STATUS_CODE, responseStatusCode));
    attributes.add(satisfies(URL_FULL, val -> val.startsWith("http://")));
    attributes.add(satisfies(SERVER_ADDRESS, v -> v.isInstanceOf(String.class)));
    attributes.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
    attributes.add(
        satisfies(
            SERVER_PORT,
            val ->
                val.satisfiesAnyOf(
                    v -> assertThat(v).isNull(), v -> assertThat(v).isInstanceOf(Number.class))));

    return span.hasName("S3." + rpcMethod)
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(attributes);
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  static SpanDataAssert sns(SpanDataAssert span, String topicArn, String rpcMethod) {
    List<AttributeAssertion> attributes = new ArrayList<>();
    attributes.add(equalTo(stringKey("aws.agent"), "java-aws-sdk"));
    if (topicArn != null) {
      attributes.add(equalTo(MESSAGING_DESTINATION_NAME, topicArn));
    }
    attributes.add(satisfies(AWS_SNS_TOPIC_ARN, v -> v.isInstanceOf(String.class)));
    attributes.add(satisfies(AWS_REQUEST_ID, v -> v.isInstanceOf(String.class)));
    attributes.add(rpcSystemAssertion("aws-api"));
    attributes.addAll(rpcMethodAssertions("AmazonSNS", rpcMethod));
    attributes.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
    attributes.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
    attributes.add(satisfies(URL_FULL, val -> val.startsWith("http://")));
    attributes.add(satisfies(SERVER_ADDRESS, v -> v.isInstanceOf(String.class)));
    attributes.add(equalTo(NETWORK_PROTOCOL_VERSION, "1.1"));
    attributes.add(
        satisfies(
            SERVER_PORT,
            val ->
                val.satisfiesAnyOf(
                    v -> assertThat(v).isNull(), v -> assertThat(v).isInstanceOf(Number.class))));

    return span.hasName("SNS." + rpcMethod)
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(attributes);
  }
}
