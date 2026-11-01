/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.UrlAttributes.URL_QUERY;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class ApiGatewayProxyAttributesExtractorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void redactsSensitiveQueryParameters() {
    Attributes attributes = attributes(HttpConstants.SENSITIVE_QUERY_PARAMETERS);
    assertThat(attributes.get(URL_FULL))
        .isEqualTo("https://localhost:123/hello?q=value&sig=REDACTED");
    assertThat(attributes.get(URL_QUERY)).isEqualTo("q=value&sig=REDACTED");
  }

  @Test
  void redactsConfiguredQueryParameters() {
    Attributes attributes = attributes(singleton("q"));
    assertThat(attributes.get(URL_FULL))
        .isEqualTo("https://localhost:123/hello?q=REDACTED&sig=secret");
    assertThat(attributes.get(URL_QUERY)).isEqualTo("q=REDACTED&sig=secret");
  }

  @Test
  void encodesQueryParameters() {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("q name", "a b&c=d?e");
    query.put("sig", "secret");

    Attributes attributes = attributes(query, HttpConstants.SENSITIVE_QUERY_PARAMETERS);
    assertThat(attributes.get(URL_QUERY)).isEqualTo("q+name=a+b%26c%3Dd%3Fe&sig=REDACTED");
    assertThat(attributes.get(URL_FULL))
        .isEqualTo("https://localhost:123/hello?q+name=a+b%26c%3Dd%3Fe&sig=REDACTED");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void omitsAbsentQuery(Map<String, String> query) {
    Attributes attributes = attributes(query, HttpConstants.SENSITIVE_QUERY_PARAMETERS);
    assertThat(attributes.get(URL_QUERY)).isNull();
    assertThat(attributes.get(URL_FULL)).isEqualTo("https://localhost:123/hello");
  }

  @ParameterizedTest
  @MethodSource("responseStatusCodes")
  void classifiesHttpResponse(Integer statusCode) {
    AwsLambdaRequest request = awsRequest(new APIGatewayProxyRequestEvent());
    Object response =
        statusCode == null ? null : new APIGatewayProxyResponseEvent().withStatusCode(statusCode);
    ApiGatewayProxyAttributesExtractor extractor = extractor();
    ApiGatewayProxySpanStatusExtractor spanStatusExtractor =
        new ApiGatewayProxySpanStatusExtractor();
    AttributesBuilder attributes = Attributes.builder();
    SpanStatusBuilder spanStatusBuilder = mock(SpanStatusBuilder.class);

    extractor.onEnd(attributes, Context.root(), request, response, null);
    spanStatusExtractor.extract(spanStatusBuilder, request, response, null);

    Attributes extractedAttributes = attributes.build();
    assertThat(extractedAttributes.get(HTTP_RESPONSE_STATUS_CODE))
        .isEqualTo(statusCode == null ? null : statusCode.longValue());
    boolean isError = statusCode != null && (statusCode < 100 || statusCode >= 500);
    assertThat(extractedAttributes.get(ERROR_TYPE))
        .isEqualTo(isError ? statusCode.toString() : null);
    if (isError) {
      verify(spanStatusBuilder).setStatus(StatusCode.ERROR);
    } else {
      verifyNoInteractions(spanStatusBuilder);
    }
  }

  @Test
  void fallsBackToExceptionStatus() {
    AwsLambdaRequest request = awsRequest(new APIGatewayProxyRequestEvent());
    Throwable error = new IllegalStateException("handler failed");
    ApiGatewayProxyAttributesExtractor extractor = extractor();
    ApiGatewayProxySpanStatusExtractor spanStatusExtractor =
        new ApiGatewayProxySpanStatusExtractor();
    AttributesBuilder attributes = Attributes.builder();
    SpanStatusBuilder spanStatusBuilder = mock(SpanStatusBuilder.class);

    extractor.onEnd(attributes, Context.root(), request, null, error);
    spanStatusExtractor.extract(spanStatusBuilder, request, null, error);

    assertThat(attributes.build().get(ERROR_TYPE)).isEqualTo(IllegalStateException.class.getName());
    verify(spanStatusBuilder).setStatus(StatusCode.ERROR);
  }

  @ParameterizedTest
  @MethodSource("nonHttpRequests")
  void nonHttpResponseIsNotClassified(Object input) {
    assertNonHttpOutcome(input, new APIGatewayProxyResponseEvent().withStatusCode(500), null);
  }

  @ParameterizedTest
  @MethodSource("nonHttpRequests")
  void nonHttpExceptionUsesDefaultStatus(Object input) {
    assertNonHttpOutcome(input, null, new IllegalStateException("handler failed"));
  }

  private static Stream<Integer> responseStatusCodes() {
    return Stream.of(null, 99, 200, 302, 400, 404, 499, 500, 503);
  }

  private static Stream<Object> nonHttpRequests() {
    return Stream.of("generic invocation", new ScheduledEvent(), new SQSEvent());
  }

  private static void assertNonHttpOutcome(Object input, Object response, Throwable error) {
    com.amazonaws.services.lambda.runtime.Context awsContext =
        mock(com.amazonaws.services.lambda.runtime.Context.class);
    when(awsContext.getFunctionName()).thenReturn("test_function");
    when(awsContext.getAwsRequestId()).thenReturn("request-id");
    AwsLambdaRequest request = AwsLambdaRequest.create(awsContext, input, emptyMap());
    AwsLambdaFunctionInstrumenter instrumenter =
        AwsLambdaEventsInstrumenterFactory.createInstrumenter(
            testing.getOpenTelemetrySdk(),
            "test",
            HttpConstants.KNOWN_METHODS,
            HttpConstants.SENSITIVE_QUERY_PARAMETERS);

    Context context = instrumenter.start(Context.root(), request);
    instrumenter.end(context, request, response, error);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test_function")
                        .hasKind(SpanKind.SERVER)
                        .hasStatus(error == null ? StatusData.unset() : StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("faas.invocation.id"), "request-id"))));
  }

  private static AwsLambdaRequest awsRequest(Object input) {
    com.amazonaws.services.lambda.runtime.Context context =
        mock(com.amazonaws.services.lambda.runtime.Context.class);
    when(context.getFunctionName()).thenReturn("test_function");
    when(context.getAwsRequestId()).thenReturn("request-id");
    return AwsLambdaRequest.create(context, input, emptyMap());
  }

  private static ApiGatewayProxyAttributesExtractor extractor() {
    return new ApiGatewayProxyAttributesExtractor(
        HttpConstants.KNOWN_METHODS, HttpConstants.SENSITIVE_QUERY_PARAMETERS);
  }

  private static Attributes attributes(Set<String> sensitiveQueryParameters) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("q", "value");
    query.put("sig", "secret");
    return attributes(query, sensitiveQueryParameters);
  }

  private static Attributes attributes(
      Map<String, String> query, Set<String> sensitiveQueryParameters) {
    ApiGatewayProxyAttributesExtractor extractor =
        new ApiGatewayProxyAttributesExtractor(
            HttpConstants.KNOWN_METHODS, sensitiveQueryParameters);

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Host", "localhost:123");
    headers.put("X-Forwarded-Proto", "https");

    APIGatewayProxyRequestEvent request =
        new APIGatewayProxyRequestEvent()
            .withHttpMethod("GET")
            .withPath("/hello")
            .withHeaders(headers);
    request.setQueryStringParameters(query);

    AttributesBuilder attributes = Attributes.builder();
    extractor.onRequest(attributes, request);
    return attributes.build();
  }
}
