/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.SdkClientException;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class AbstractS3ClientTest extends AbstractBaseAwsClientTest {

  public abstract AmazonS3ClientBuilder configureClient(AmazonS3ClientBuilder client);

  private final AmazonS3ClientBuilder clientBuilder =
      AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true);

  @Override
  protected boolean hasRequestId() {
    return false;
  }

  @ParameterizedTest
  @MethodSource("provideArguments")
  public void testSendRequestWithMockedResponse(
      String operation,
      String method,
      Function<AmazonS3, Object> call,
      List<AttributeAssertion> additionalAttributes)
      throws Exception {

    AmazonS3 client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    Object response = call.apply(client);
    assertRequestWithMockedResponse(
        response, client, "S3", operation, method, additionalAttributes);
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(
            "CreateBucket",
            "PUT",
            (Function<AmazonS3, Object>) c -> c.createBucket("testbucket"),
            singletonList(equalTo(stringKey("aws.bucket.name"), "testbucket"))),
        Arguments.of(
            "GetObject",
            "GET",
            (Function<AmazonS3, Object>) c -> c.getObject("someBucket", "someKey"),
            singletonList(equalTo(stringKey("aws.bucket.name"), "someBucket"))));
  }

  @Test
  public void testSendRequestToClosedPort() {
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    AmazonS3 client =
        configureClient(clientBuilder)
            .withCredentials(credentialsProvider)
            .withClientConfiguration(
                new ClientConfiguration()
                    .withRetryPolicy(
                        PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(0)))
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    "http://127.0.0.1:" + UNUSABLE_PORT, "us-east-1"))
            .build();

    Throwable caught = catchThrowable(() -> client.getObject("someBucket", "someKey"));
    assertThat(caught).isInstanceOf(SdkClientException.class);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("S3.GetObject")
                            .hasKind(CLIENT)
                            .hasStatus(StatusData.error())
                            .hasException(caught)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(URL_FULL, "http://127.0.0.1:" + UNUSABLE_PORT),
                                equalTo(HTTP_REQUEST_METHOD, "GET"),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                equalTo(SERVER_PORT, 61),
                                equalTo(RPC_SYSTEM, "aws-api"),
                                equalTo(RPC_SERVICE, "Amazon S3"),
                                equalTo(RPC_METHOD, "GetObject"),
                                equalTo(
                                    stringKey("aws.endpoint"), "http://127.0.0.1:" + UNUSABLE_PORT),
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(stringKey("aws.bucket.name"), "someBucket"),
                                equalTo(ERROR_TYPE, SdkClientException.class.getName()))));
  }

  @Test
  void testTimeoutAndRetryErrorsNotCaptured() {
    server.enqueue(HttpResponse.delayed(HttpResponse.of(HttpStatus.OK), Duration.ofSeconds(5)));
    server.enqueue(HttpResponse.delayed(HttpResponse.of(HttpStatus.OK), Duration.ofSeconds(5)));
    AmazonS3 client =
        configureClient(AmazonS3ClientBuilder.standard())
            .withClientConfiguration(
                new ClientConfiguration()
                    .withRequestTimeout(50 /* ms */)
                    .withRetryPolicy(
                        PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(1)))
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    server.httpUri().toString(), "us-east-1"))
            .build();

    Throwable caught = catchThrowable(() -> client.getObject("someBucket", "someKey"));
    assertThat(caught).isInstanceOf(AmazonClientException.class);
    assertThat(Span.current().getSpanContext().isValid()).isFalse();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("S3.GetObject")
                            .hasKind(CLIENT)
                            .hasStatus(StatusData.error())
                            .hasNoParent()
                            .hasException(
                                new SdkClientException(
                                    "Unable to execute HTTP request: Request did not complete before the request timeout configuration."))
                            .hasAttributesSatisfyingExactly(
                                equalTo(URL_FULL, server.httpUri().toString()),
                                equalTo(HTTP_REQUEST_METHOD, "GET"),
                                equalTo(SERVER_PORT, server.httpPort()),
                                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                equalTo(RPC_SYSTEM, "aws-api"),
                                equalTo(RPC_SERVICE, "Amazon S3"),
                                equalTo(RPC_METHOD, "GetObject"),
                                equalTo(stringKey("aws.endpoint"), server.httpUri().toString()),
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(stringKey("aws.bucket.name"), "someBucket"),
                                equalTo(ERROR_TYPE, SdkClientException.class.getName()))));
  }
}
