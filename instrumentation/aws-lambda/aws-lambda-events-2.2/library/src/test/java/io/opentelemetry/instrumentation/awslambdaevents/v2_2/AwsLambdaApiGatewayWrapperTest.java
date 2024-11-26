/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrappedLambda;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.incubating.CloudIncubatingAttributes;
import io.opentelemetry.semconv.incubating.FaasIncubatingAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AwsLambdaApiGatewayWrapperTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private Context context;

  @BeforeEach
  void setUp() {
    when(context.getFunctionName()).thenReturn("my_function");
    when(context.getAwsRequestId()).thenReturn("1-22-333");
    when(context.getInvokedFunctionArn())
        .thenReturn("arn:aws:lambda:us-east-1:123456789:function:test");
  }

  @AfterEach
  void tearDown() {
    assertThat(testing.forceFlushCalled()).isTrue();
  }

  @Test
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v2_2.AwsLambdaApiGatewayWrapperTest$TestRequestHandlerApiGateway::handleRequest")
  void tracedWithHttpPropagation() {
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
            .withBody("hello")
            .withQueryStringParameters(query)
            .withHeaders(headers);

    APIGatewayProxyResponseEvent result =
        (APIGatewayProxyResponseEvent) wrapper.handleRequest(input, context);

    assertThat(result.getBody()).isEqualTo("world");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /hello/{param}")
                        .hasKind(SpanKind.SERVER)
                        .hasTraceId("4fd0b6131f19f39af59518d127b0cafe")
                        .hasParentSpanId("0000000000000456")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CloudIncubatingAttributes.CLOUD_RESOURCE_ID,
                                "arn:aws:lambda:us-east-1:123456789:function:test"),
                            equalTo(CloudIncubatingAttributes.CLOUD_ACCOUNT_ID, "123456789"),
                            equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333"),
                            equalTo(FaasIncubatingAttributes.FAAS_TRIGGER, "http"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(USER_AGENT_ORIGINAL, "Test Client"),
                            equalTo(URL_FULL, "http://localhost:123/hello/world?a=b&c=d"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L))));
  }

  @Test
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v2_2.AwsLambdaApiGatewayWrapperTest$TestRequestHandlerApiGateway::handleRequest")
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
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CloudIncubatingAttributes.CLOUD_RESOURCE_ID,
                                "arn:aws:lambda:us-east-1:123456789:function:test"),
                            equalTo(CloudIncubatingAttributes.CLOUD_ACCOUNT_ID, "123456789"),
                            equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333"),
                            equalTo(FaasIncubatingAttributes.FAAS_TRIGGER, "http"))));
  }

  @Test
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v2_2.AwsLambdaApiGatewayWrapperTest$TestRequestHandlerString::handleRequest")
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
                                CloudIncubatingAttributes.CLOUD_RESOURCE_ID,
                                "arn:aws:lambda:us-east-1:123456789:function:test"),
                            equalTo(CloudIncubatingAttributes.CLOUD_ACCOUNT_ID, "123456789"),
                            equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333"),
                            equalTo(FaasIncubatingAttributes.FAAS_TRIGGER, "http"))));
  }

  @Test
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v2_2.AwsLambdaApiGatewayWrapperTest$TestRequestHandlerInteger::handleRequest")
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
                                CloudIncubatingAttributes.CLOUD_RESOURCE_ID,
                                "arn:aws:lambda:us-east-1:123456789:function:test"),
                            equalTo(CloudIncubatingAttributes.CLOUD_ACCOUNT_ID, "123456789"),
                            equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333"),
                            equalTo(FaasIncubatingAttributes.FAAS_TRIGGER, "http"))));
  }

  @Test
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v2_2.AwsLambdaApiGatewayWrapperTest$TestRequestHandlerCustomType::handleRequest")
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
                                CloudIncubatingAttributes.CLOUD_RESOURCE_ID,
                                "arn:aws:lambda:us-east-1:123456789:function:test"),
                            equalTo(CloudIncubatingAttributes.CLOUD_ACCOUNT_ID, "123456789"),
                            equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333"),
                            equalTo(FaasIncubatingAttributes.FAAS_TRIGGER, "http"))));
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
      }
      throw new IllegalStateException("bad request");
    }
  }

  public static final class TestRequestHandlerString implements RequestHandler<String, String> {

    @Override
    public String handleRequest(String input, Context context) {
      if (input.equals("hello")) {
        return "world";
      }
      throw new IllegalArgumentException("bad argument");
    }
  }

  public static final class TestRequestHandlerInteger implements RequestHandler<Integer, String> {

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

  public static final class TestRequestHandlerCustomType
      implements RequestHandler<CustomType, String> {

    @Override
    public String handleRequest(CustomType input, Context context) {
      if (input.key.equals("hello")) {
        return input.value;
      }
      throw new IllegalArgumentException("bad argument");
    }
  }
}
