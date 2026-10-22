/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.FaasIncubatingAttributes.FAAS_INVOCATION_ID;
import static java.util.Collections.emptyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AwsLambdaEventsInstrumenterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @MethodSource("nonHttpRequests")
  void nonHttpResponseIsNotClassified(Object input) {
    assertNonHttpOutcome(input, new APIGatewayProxyResponseEvent().withStatusCode(500), null);
  }

  @ParameterizedTest
  @MethodSource("nonHttpRequests")
  void nonHttpExceptionIsNotClassified(Object input) {
    assertNonHttpOutcome(input, null, new IllegalStateException("handler failed"));
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
                            equalTo(FAAS_INVOCATION_ID, "request-id"))));
  }
}
