/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_DYNAMODB_TABLE_NAMES;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

class DynamoDbErrorsTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("deprecation") // using deprecated semconv
  void sdkProtocolOutcome(boolean missingItem) {
    MockWebServerExtension server = new MockWebServerExtension();
    server.start();
    try {
      server.enqueue(
          HttpResponse.of(
              missingItem ? HttpStatus.OK : HttpStatus.BAD_REQUEST,
              MediaType.JSON_UTF_8,
              missingItem
                  ? "{}"
                  : "{\"__type\":\"com.amazonaws.dynamodb.v20120810#ResourceNotFoundException\",\"message\":\"Requested resource not found\"}"));
      try (DynamoDbClient client =
          DynamoDbClient.builder()
              .endpointOverride(server.httpUri())
              .region(Region.US_WEST_2)
              .credentialsProvider(
                  StaticCredentialsProvider.create(AwsBasicCredentials.create("access", "secret")))
              .overrideConfiguration(
                  ClientOverrideConfiguration.builder()
                      .retryPolicy(RetryPolicy.none())
                      .addExecutionInterceptor(
                          AwsSdkTelemetry.create(testing.getOpenTelemetry())
                              .createExecutionInterceptor())
                      .build())
              .build()) {
        GetItemRequest request =
            GetItemRequest.builder()
                .tableName("items")
                .key(singletonMap("id", AttributeValue.builder().s("missing").build()))
                .build();
        if (missingItem) {
          assertThat(client.getItem(request).item()).isEmpty();
        } else {
          assertThatThrownBy(() -> client.getItem(request))
              .isInstanceOf(ResourceNotFoundException.class);
        }
      }
      String errorType = missingItem ? null : "ResourceNotFoundException";
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("DynamoDb.GetItem")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasStatus(missingItem ? StatusData.unset() : StatusData.error())
                          .hasAttributesSatisfyingExactly(
                              equalTo(DB_SYSTEM_NAME, "aws.dynamodb"),
                              equalTo(DB_COLLECTION_NAME, "items"),
                              equalTo(DB_OPERATION_NAME, "GetItem"),
                              equalTo(HTTP_RESPONSE_STATUS_CODE, missingItem ? 200L : null),
                              equalTo(ERROR_TYPE, errorType),
                              equalTo(SERVER_ADDRESS, "127.0.0.1"),
                              equalTo(SERVER_PORT, server.httpPort()),
                              equalTo(HTTP_REQUEST_METHOD, "POST"),
                              equalTo(URL_FULL, server.httpUri() + "/"),
                              equalTo(AWS_DYNAMODB_TABLE_NAMES, singletonList("items")),
                              equalTo(AWS_REQUEST_ID, missingItem ? "UNKNOWN" : null),
                              equalTo(RPC_SYSTEM, "aws-api"),
                              equalTo(RPC_SERVICE, "DynamoDb"),
                              equalTo(RPC_METHOD, "GetItem"))));
      assertDurationMetric(
          testing,
          "io.opentelemetry.aws-sdk-2.2",
          missingItem
              ? new AttributeKey<?>[] {DB_SYSTEM_NAME, DB_COLLECTION_NAME, DB_OPERATION_NAME}
              : new AttributeKey<?>[] {
                DB_SYSTEM_NAME, DB_COLLECTION_NAME, DB_OPERATION_NAME, ERROR_TYPE
              });
      testing.waitAndAssertMetrics(
          "io.opentelemetry.aws-sdk-2.2",
          "db.client.operation.duration",
          metrics ->
              metrics
                  .singleElement()
                  .satisfies(
                      metric ->
                          assertThat(metric.getHistogramData().getPoints())
                              .singleElement()
                              .satisfies(
                                  point -> {
                                    assertThat(point.getCount()).isEqualTo(1);
                                    assertThat(point.getAttributes().asMap())
                                        .isEqualTo(
                                            Attributes.builder()
                                                .put(DB_SYSTEM_NAME, "aws.dynamodb")
                                                .put(DB_COLLECTION_NAME, "items")
                                                .put(DB_OPERATION_NAME, "GetItem")
                                                .put(ERROR_TYPE, errorType)
                                                .build()
                                                .asMap());
                                  })));
    } finally {
      server.stop();
    }
  }
}
