package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;


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
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

class AwsSpanAssertions {
  private AwsSpanAssertions() {}

  static SpanDataAssert sqs(
      SpanDataAssert span,
      String queueName,
      String queueUrl,
      String rpcMethod,
      String requestMethod,
      int responseStatusCode) {
    return span.hasName("SQS." + rpcMethod)
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            satisfies(stringKey("aws.endpoint"), v -> v.isInstanceOf(String.class)),
            equalTo(stringKey("aws.queue.name"), queueName),
            equalTo(stringKey("aws.queue.url"), queueUrl),
            satisfies(AWS_REQUEST_ID, v -> v.isInstanceOf(String.class)),
            equalTo(RPC_METHOD, rpcMethod),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "AmazonSQS"),
            equalTo(HTTP_REQUEST_METHOD, requestMethod),
            equalTo(HTTP_RESPONSE_STATUS_CODE, responseStatusCode),
            satisfies(URL_FULL, val -> val.startsWith("http://")),
            satisfies(SERVER_ADDRESS, v -> v.isInstanceOf(String.class)),
            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
            satisfies(SERVER_PORT,
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
            satisfies(stringKey("aws.endpoint"), v -> v.isInstanceOf(String.class)),
            equalTo(stringKey("aws.bucket.name"), bucketName),
            equalTo(RPC_METHOD, rpcMethod),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "Amazon S3"),
            equalTo(HTTP_REQUEST_METHOD, requestMethod),
            equalTo(HTTP_RESPONSE_STATUS_CODE, responseStatusCode),
            satisfies(URL_FULL, val -> val.startsWith("http://")),
            satisfies(SERVER_ADDRESS, v -> v.isInstanceOf(String.class)),
            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
            satisfies(SERVER_PORT,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(),
                        v -> assertThat(v).isInstanceOf(Number.class))));
  }


}
