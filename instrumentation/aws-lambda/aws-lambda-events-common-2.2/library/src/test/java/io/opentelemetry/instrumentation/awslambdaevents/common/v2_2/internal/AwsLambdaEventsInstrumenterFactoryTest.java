/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.incubating.FaasIncubatingAttributes.FAAS_INVOCATION_ID;
import static io.opentelemetry.semconv.incubating.FaasIncubatingAttributes.FAAS_TRIGGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.TracingRequestHandler;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SchemaUrls;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AwsLambdaEventsInstrumenterFactoryTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final Context awsContext = mock(Context.class);

  @BeforeEach
  void setUp() {
    when(awsContext.getFunctionName()).thenReturn("my_function");
    when(awsContext.getAwsRequestId()).thenReturn("1-22-333");
  }

  private static Stream<Object> requests() {
    return Stream.of(new Object(), new ScheduledEvent(), new APIGatewayProxyRequestEvent());
  }

  @ParameterizedTest
  @MethodSource("requests")
  void terminalInvocationFailure(Object request) {
    InvocationException error = new InvocationException();
    TracingRequestHandler<Object, Object> handler =
        new TestHandler() {
          @Override
          protected Object doHandleRequest(Object input, Context context) {
            throw error;
          }
        };

    assertThat(catchThrowable(() -> handler.handleRequest(request, awsContext))).isSameAs(error);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("my_function")
                        .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                        .hasKind(SpanKind.SERVER)
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(FAAS_INVOCATION_ID, "1-22-333"),
                            equalTo(
                                FAAS_TRIGGER,
                                request instanceof APIGatewayProxyRequestEvent ? "http" : null),
                            equalTo(ERROR_TYPE, InvocationException.class.getCanonicalName()))));
    assertThat(testing.forceFlushCalled()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 400, 500})
  void handledHttpResponse(int statusCode) {
    APIGatewayProxyResponseEvent response =
        new APIGatewayProxyResponseEvent().withStatusCode(statusCode);
    TracingRequestHandler<Object, Object> handler =
        new TestHandler() {
          @Override
          protected Object doHandleRequest(Object input, Context context) {
            return response;
          }
        };

    assertThat(handler.handleRequest(new APIGatewayProxyRequestEvent(), awsContext))
        .isSameAs(response);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("my_function")
                        .hasKind(SpanKind.SERVER)
                        .hasStatus(StatusData.unset())
                        .hasAttributesSatisfyingExactly(
                            equalTo(FAAS_INVOCATION_ID, "1-22-333"),
                            equalTo(FAAS_TRIGGER, "http"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, statusCode))));
    assertThat(testing.forceFlushCalled()).isTrue();
  }

  private abstract static class TestHandler extends TracingRequestHandler<Object, Object> {
    TestHandler() {
      super(
          testing.getOpenTelemetrySdk(),
          Duration.ofSeconds(1),
          AwsLambdaEventsInstrumenterFactory.createInstrumenter(
              testing.getOpenTelemetrySdk(),
              "io.opentelemetry.aws-lambda-events-common-2.2",
              HttpConstants.KNOWN_METHODS,
              HttpConstants.SENSITIVE_QUERY_PARAMETERS));
    }
  }

  private static class InvocationException extends RuntimeException {}
}
