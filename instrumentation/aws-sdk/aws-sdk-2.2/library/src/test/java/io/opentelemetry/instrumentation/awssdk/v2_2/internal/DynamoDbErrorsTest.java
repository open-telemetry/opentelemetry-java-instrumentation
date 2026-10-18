/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewRpcSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_DYNAMODB_TABLE_NAMES;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static java.util.Collections.singletonMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

class DynamoDbErrorsTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @BeforeAll
  static void configureStructuredRpcSemconv() {
    String mode = System.getProperty("test.dynamodb.rpc-semconv");
    if (mode == null) {
      return;
    }
    DeclarativeConfigProperties semconv = mock(DeclarativeConfigProperties.class);
    when(semconv.getInt("version")).thenReturn(mode.equals("v0") ? 0 : 1);
    when(semconv.getBoolean("experimental", false)).thenReturn(true);
    when(semconv.getBoolean("dual_emit", false)).thenReturn(mode.equals("dual"));
    DeclarativeConfigProperties rpc = mock(DeclarativeConfigProperties.class);
    when(rpc.get("semconv")).thenReturn(semconv);
    DeclarativeConfigProperties general = mock(DeclarativeConfigProperties.class);
    when(general.get(anyString())).thenReturn(DeclarativeConfigProperties.empty());
    when(general.get("rpc")).thenReturn(rpc);
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    ConfigProvider configProvider = mock(ConfigProvider.class);
    when(configProvider.getGeneralInstrumentationConfig()).thenReturn(general);
    when(configProvider.getInstrumentationConfig("common"))
        .thenReturn(DeclarativeConfigProperties.empty());
    when(openTelemetry.getConfigProvider()).thenReturn(configProvider);
    GlobalOpenTelemetry.resetForTest();
    GlobalOpenTelemetry.set(openTelemetry);
  }

  @AfterAll
  static void resetGlobal() {
    if (System.getProperty("test.dynamodb.rpc-semconv") != null) {
      GlobalOpenTelemetry.resetForTest();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
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
                  : "{\"__type\":\"com.amazonaws.dynamodb.v20120810#ConditionalCheckFailedException\",\"message\":\"private value\"}"));
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
          assertThatThrownBy(() -> client.getItem(request)).isInstanceOf(DynamoDbException.class);
        }
      }
      String errorType = missingItem ? null : "ConditionalCheckFailedException";
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("DynamoDb.GetItem")
                          .hasKind(SpanKind.CLIENT)
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
                              equalTo(stringKey("rpc.system"), emitOldRpcSemconv() ? "aws-api" : null),
                              equalTo(stringKey("rpc.service"), emitOldRpcSemconv() ? "DynamoDb" : null),
                              equalTo(stringKey("rpc.method"), emitPreviewRpcSemconv() ? null : "GetItem"),
                              equalTo(stringKey("rpc.system.name"), emitPreviewRpcSemconv() ? "aws-api" : null))));
      assertDurationMetric(
          testing,
          "io.opentelemetry.aws-sdk-2.2",
          missingItem
              ? new AttributeKey<?>[] {
                DB_SYSTEM_NAME, DB_COLLECTION_NAME, DB_OPERATION_NAME
              }
              : new AttributeKey<?>[] {
                DB_SYSTEM_NAME,
                DB_COLLECTION_NAME,
                DB_OPERATION_NAME,
                ERROR_TYPE
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
                                    assertThat(point.getAttributes().get(ERROR_TYPE))
                                        .isEqualTo(errorType);
                                  })));
    } finally {
      server.stop();
    }
  }

  @ParameterizedTest
  @MethodSource("errors")
  void finalOutcome(Throwable error, String errorType) {
    Instrumenter<ExecutionAttributes, Response> instrumenter =
        new AwsSdkInstrumenterFactory(
                testing.getOpenTelemetry(),
                null,
                IncludeExclude.builder().build(),
                false,
                false,
                false)
            .dynamoDbInstrumenter();
    ExecutionAttributes request =
        new ExecutionAttributes()
            .putAttribute(SdkExecutionAttribute.SERVICE_NAME, "DynamoDb")
            .putAttribute(SdkExecutionAttribute.OPERATION_NAME, "GetItem")
            .putAttribute(
                TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE,
                GetItemRequest.builder().tableName("items").build());
    Context context = instrumenter.start(Context.root(), request);
    // An empty GetItem response represents a missing item, not an error.
    Response response =
        new Response(
            SdkHttpResponse.builder().statusCode(error == null ? 200 : 400).build(),
            GetItemResponse.builder().build());
    instrumenter.end(context, request, response, error);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DynamoDb.GetItem")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasStatus(
                            StatusData.create(
                                error == null ? StatusCode.UNSET : StatusCode.ERROR, ""))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, "aws.dynamodb"),
                            equalTo(DB_COLLECTION_NAME, "items"),
                            equalTo(DB_OPERATION_NAME, "GetItem"),
                            equalTo(ERROR_TYPE, errorType),
                            equalTo(
                                stringKey("rpc.system"), emitOldRpcSemconv() ? "aws-api" : null),
                            equalTo(
                                stringKey("rpc.service"), emitOldRpcSemconv() ? "DynamoDb" : null),
                            equalTo(
                                stringKey("rpc.method"),
                                emitPreviewRpcSemconv() ? null : "GetItem"),
                            equalTo(
                                stringKey("rpc.system.name"),
                                emitPreviewRpcSemconv() ? "aws-api" : null))));
    assertDurationMetric(
        testing,
        "io.opentelemetry.aws-sdk-2.2",
        error == null
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
                                  assertThat(point.getAttributes().get(ERROR_TYPE))
                                      .isEqualTo(errorType);
                                })));
  }

  private static Stream<Arguments> errors() {
    return Stream.of(
        Arguments.of(
            DynamoDbException.builder()
                .awsErrorDetails(
                    AwsErrorDetails.builder().errorCode("ConditionalCheckFailedException").build())
                .statusCode(400)
                .message("private value")
                .build(),
            "ConditionalCheckFailedException"),
        Arguments.of(
            DynamoDbException.builder().statusCode(400).message("private value").build(),
            DynamoDbException.class.getName()),
        Arguments.of(
            DynamoDbException.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("").build())
                .build(),
            DynamoDbException.class.getName()),
        Arguments.of(
            SdkClientException.create("private value"), SdkClientException.class.getName()),
        Arguments.of(
            new IllegalStateException("private value"), IllegalStateException.class.getName()),
        Arguments.of(null, null));
  }
}
