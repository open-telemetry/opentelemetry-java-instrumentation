/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v3_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_QUERY;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
import static io.opentelemetry.semconv.incubating.CloudIncubatingAttributes.CLOUD_ACCOUNT_ID;
import static io.opentelemetry.semconv.incubating.CloudIncubatingAttributes.CLOUD_RESOURCE_ID;
import static io.opentelemetry.semconv.incubating.FaasIncubatingAttributes.FAAS_INVOCATION_ID;
import static io.opentelemetry.semconv.incubating.FaasIncubatingAttributes.FAAS_TRIGGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrappedLambda;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AwsLambdaApiGatewayWrapperTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private Context context;

  @BeforeEach
  void setUp() {
    when(context.getFunctionName()).thenReturn("my_function");
    when(context.getAwsRequestId()).thenReturn("1-22-333");
    AwsLambdaTestUtils.stubInvokedFunctionArn(context);
  }

  @AfterEach
  void tearDown() {
    assertThat(testing.forceFlushCalled()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 404, 500})
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v3_11.AwsLambdaApiGatewayWrapperTest$TestRequestHandlerApiGateway::handleRequest")
  void tracedWithHttpPropagation(int statusCode) {
    TracingRequestApiGatewayWrapper wrapper =
        new TracingRequestApiGatewayWrapper(
            testing.getOpenTelemetrySdk(),
            WrappedLambda.fromConfiguration(),
            TracingRequestApiGatewayWrapper::map);

    Map<String, String> headers = new HashMap<>();
    headers.put("traceparent", "00-4fd0b6131f19f39af59518d127b0cafe-0000000000000456-01");
    headers.put("User-Agent", "Test Client");
    headers.put("host", "localhost:123");
    headers.put("X-FORWARDED-PROTO", "http");
    Map<String, String> query = new HashMap<>();
    query.put("a", "b");
    query.put("c", "d");
    APIGatewayProxyRequestEvent input =
        new APIGatewayProxyRequestEvent()
            .withHttpMethod("GET")
            .withResource("/hello/{param}")
            .withPath("/hello/world")
            .withBody(statusCode == 200 ? "hello" : Integer.toString(statusCode))
            .withQueryStringParameters(query)
            .withHeaders(headers);

    APIGatewayProxyResponseEvent result =
        (APIGatewayProxyResponseEvent) wrapper.handleRequest(input, context);

    assertThat(result.getBody()).isEqualTo(statusCode == 200 ? "world" : null);
    assertThat(result.getStatusCode()).isEqualTo(statusCode);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /hello/{param}")
                        .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                        .hasKind(SpanKind.SERVER)
                        .hasStatus(statusCode >= 500 ? StatusData.error() : StatusData.unset())
                        .hasEventsSatisfyingExactly()
                        .hasTraceId("4fd0b6131f19f39af59518d127b0cafe")
                        .hasParentSpanId("0000000000000456")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CLOUD_RESOURCE_ID,
                                AwsLambdaTestUtils.supportsInvokedFunctionArn()
                                    ? "arn:aws:lambda:us-east-1:123456789:function:test"
                                    : null),
                            equalTo(
                                CLOUD_ACCOUNT_ID,
                                AwsLambdaTestUtils.supportsInvokedFunctionArn()
                                    ? "123456789"
                                    : null),
                            equalTo(FAAS_INVOCATION_ID, "1-22-333"),
                            equalTo(FAAS_TRIGGER, "http"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(URL_PATH, "/hello/world"),
                            equalTo(URL_SCHEME, "http"),
                            equalTo(HTTP_ROUTE, "/hello/{param}"),
                            equalTo(USER_AGENT_ORIGINAL, "Test Client"),
                            equalTo(URL_FULL, "http://localhost:123/hello/world?a=b&c=d"),
                            equalTo(URL_QUERY, "a=b&c=d"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, statusCode),
                            equalTo(
                                ERROR_TYPE,
                                statusCode >= 500 ? Integer.toString(statusCode) : null))));
  }

  @Test
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v3_11.AwsLambdaApiGatewayWrapperTest$TestRequestHandlerApiGateway::handleRequest")
  void handlerTraced_empty() {
    TracingRequestApiGatewayWrapper wrapper =
        new TracingRequestApiGatewayWrapper(
            testing.getOpenTelemetrySdk(),
            WrappedLambda.fromConfiguration(),
            TracingRequestApiGatewayWrapper::map);
    APIGatewayProxyResponseEvent result =
        (APIGatewayProxyResponseEvent)
            wrapper.handleRequest(new APIGatewayProxyRequestEvent().withBody("empty"), context);

    assertThat(result.getBody()).isNull();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("my_function")
                        .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                        .hasKind(SpanKind.SERVER)
                        .hasStatus(StatusData.unset())
                        .hasEventsSatisfyingExactly()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CLOUD_RESOURCE_ID,
                                AwsLambdaTestUtils.supportsInvokedFunctionArn()
                                    ? "arn:aws:lambda:us-east-1:123456789:function:test"
                                    : null),
                            equalTo(
                                CLOUD_ACCOUNT_ID,
                                AwsLambdaTestUtils.supportsInvokedFunctionArn()
                                    ? "123456789"
                                    : null),
                            equalTo(FAAS_INVOCATION_ID, "1-22-333"),
                            equalTo(FAAS_TRIGGER, "http"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, null),
                            equalTo(ERROR_TYPE, null))));
  }

  @Test
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v3_11.AwsLambdaApiGatewayWrapperTest$TestRequestHandlerString::handleRequest")
  void handlerTraced_string() {
    TracingRequestApiGatewayWrapper wrapper =
        new TracingRequestApiGatewayWrapper(
            testing.getOpenTelemetrySdk(),
            WrappedLambda.fromConfiguration(),
            TracingRequestApiGatewayWrapper::map);
    APIGatewayProxyResponseEvent result =
        (APIGatewayProxyResponseEvent)
            wrapper.handleRequest(new APIGatewayProxyRequestEvent().withBody("\"hello\""), context);

    assertThat(result.getBody()).isEqualTo("\"world\"");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("my_function")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CLOUD_RESOURCE_ID,
                                AwsLambdaTestUtils.supportsInvokedFunctionArn()
                                    ? "arn:aws:lambda:us-east-1:123456789:function:test"
                                    : null),
                            equalTo(
                                CLOUD_ACCOUNT_ID,
                                AwsLambdaTestUtils.supportsInvokedFunctionArn()
                                    ? "123456789"
                                    : null),
                            equalTo(FAAS_INVOCATION_ID, "1-22-333"),
                            equalTo(FAAS_TRIGGER, "http"))));
  }

  @Test
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v3_11.AwsLambdaApiGatewayWrapperTest$TestRequestHandlerInteger::handleRequest")
  void handlerTraced_integer() {
    TracingRequestApiGatewayWrapper wrapper =
        new TracingRequestApiGatewayWrapper(
            testing.getOpenTelemetrySdk(),
            WrappedLambda.fromConfiguration(),
            TracingRequestApiGatewayWrapper::map);
    APIGatewayProxyResponseEvent result =
        (APIGatewayProxyResponseEvent)
            wrapper.handleRequest(new APIGatewayProxyRequestEvent().withBody("1"), context);

    assertThat(result.getBody()).isEqualTo("\"world\"");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("my_function")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CLOUD_RESOURCE_ID,
                                AwsLambdaTestUtils.supportsInvokedFunctionArn()
                                    ? "arn:aws:lambda:us-east-1:123456789:function:test"
                                    : null),
                            equalTo(
                                CLOUD_ACCOUNT_ID,
                                AwsLambdaTestUtils.supportsInvokedFunctionArn()
                                    ? "123456789"
                                    : null),
                            equalTo(FAAS_INVOCATION_ID, "1-22-333"),
                            equalTo(FAAS_TRIGGER, "http"))));
  }

  @Test
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v3_11.AwsLambdaApiGatewayWrapperTest$TestRequestHandlerCustomType::handleRequest")
  void handlerTraced_customType() {
    TracingRequestApiGatewayWrapper wrapper =
        new TracingRequestApiGatewayWrapper(
            testing.getOpenTelemetrySdk(),
            WrappedLambda.fromConfiguration(),
            TracingRequestApiGatewayWrapper::map);
    APIGatewayProxyResponseEvent result =
        (APIGatewayProxyResponseEvent)
            wrapper.handleRequest(
                new APIGatewayProxyRequestEvent()
                    .withBody("{\"key\":\"hello\", \"value\":\"General Kenobi\"}"),
                context);

    assertThat(result.getBody()).isEqualTo("\"General Kenobi\"");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("my_function")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CLOUD_RESOURCE_ID,
                                AwsLambdaTestUtils.supportsInvokedFunctionArn()
                                    ? "arn:aws:lambda:us-east-1:123456789:function:test"
                                    : null),
                            equalTo(
                                CLOUD_ACCOUNT_ID,
                                AwsLambdaTestUtils.supportsInvokedFunctionArn()
                                    ? "123456789"
                                    : null),
                            equalTo(FAAS_INVOCATION_ID, "1-22-333"),
                            equalTo(FAAS_TRIGGER, "http"))));
  }

  @Test
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v3_11.AwsLambdaApiGatewayWrapperTest$TestRequestHandlerApiGateway::handleRequest")
  void classifiesHttpException() {
    TracingRequestApiGatewayWrapper wrapper =
        new TracingRequestApiGatewayWrapper(
            testing.getOpenTelemetrySdk(),
            WrappedLambda.fromConfiguration(),
            TracingRequestApiGatewayWrapper::map);
    Throwable thrown =
        catchThrowable(
            () ->
                wrapper.handleRequest(
                    new APIGatewayProxyRequestEvent().withBody("error"), context));
    assertThat(thrown).isInstanceOf(IllegalStateException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.SERVER)
                        .hasStatus(StatusData.error())
                        .hasException(emitExceptionAsSpanEvents() ? thrown : null)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CLOUD_RESOURCE_ID,
                                AwsLambdaTestUtils.supportsInvokedFunctionArn()
                                    ? "arn:aws:lambda:us-east-1:123456789:function:test"
                                    : null),
                            equalTo(
                                CLOUD_ACCOUNT_ID,
                                AwsLambdaTestUtils.supportsInvokedFunctionArn()
                                    ? "123456789"
                                    : null),
                            equalTo(FAAS_INVOCATION_ID, "1-22-333"),
                            equalTo(FAAS_TRIGGER, "http"),
                            equalTo(ERROR_TYPE, IllegalStateException.class.getName()))));
    if (emitExceptionAsLogs()) {
      testing.waitAndAssertLogRecords(
          logRecord ->
              logRecord
                  .hasSeverity(Severity.ERROR)
                  .hasEventName("faas.invocation.exception")
                  .hasException(thrown)
                  .hasTotalAttributeCount(3));
    }
  }

  public static class TestRequestHandlerApiGateway
      implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
        APIGatewayProxyRequestEvent input, Context context) {
      if (input.getBody().equals("hello")) {
        return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody("world");
      } else if (input.getBody().equals("empty")) {
        return new APIGatewayProxyResponseEvent();
      } else if (!input.getBody().equals("error")) {
        return new APIGatewayProxyResponseEvent().withStatusCode(Integer.parseInt(input.getBody()));
      }
      throw new IllegalStateException("bad request");
    }
  }

  public static class TestRequestHandlerString implements RequestHandler<String, String> {

    @Override
    public String handleRequest(String input, Context context) {
      if (input.equals("hello")) {
        return "world";
      }
      throw new IllegalArgumentException("bad argument");
    }
  }

  public static class TestRequestHandlerInteger implements RequestHandler<Integer, String> {

    @Override
    public String handleRequest(Integer input, Context context) {
      if (input == 1) {
        return "world";
      }
      throw new IllegalArgumentException("bad argument");
    }
  }

  public static class CustomType {
    public String key;
    public String value;
  }

  public static class TestRequestHandlerCustomType implements RequestHandler<CustomType, String> {

    @Override
    public String handleRequest(CustomType input, Context context) {
      if (input.key.equals("hello")) {
        return input.value;
      }
      throw new IllegalArgumentException("bad argument");
    }
  }
}
