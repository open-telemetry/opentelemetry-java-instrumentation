/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractAwsLambdaTest {

  protected static String doHandleRequest(String input, Context context) {
    if (input.equals("hello")) {
      return "world";
    }
    throw new IllegalArgumentException("bad argument");
  }

  protected abstract RequestHandler<String, String> handler();

  protected abstract InstrumentationExtension testing();

  @Mock private Context context;

  @BeforeEach
  void setUp() {
    when(context.getFunctionName()).thenReturn("my_function");
    when(context.getAwsRequestId()).thenReturn("1-22-333");
  }

  @AfterEach
  void tearDown() {
    assertThat(testing().forceFlushCalled()).isTrue();
  }

  @Test
  void handlerTraced() {
    String result = handler().handleRequest("hello", context);
    assertThat(result).isEqualTo("world");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("my_function")
                            .hasKind(SpanKind.SERVER)
                            .hasAttributesSatisfying(
                                attrs ->
                                    assertThat(attrs)
                                        .containsOnly(
                                            entry(
                                                SemanticAttributes.FAAS_EXECUTION, "1-22-333")))));
  }

  @Test
  void handlerTracedWithException() {
    Throwable thrown = catchThrowable(() -> handler().handleRequest("goodbye", context));
    assertThat(thrown).isInstanceOf(IllegalArgumentException.class);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("my_function")
                            .hasKind(SpanKind.SERVER)
                            .hasStatus(StatusData.error())
                            .hasException(thrown)
                            .hasAttributesSatisfying(
                                attrs ->
                                    assertThat(attrs)
                                        .containsOnly(
                                            entry(
                                                SemanticAttributes.FAAS_EXECUTION, "1-22-333")))));
  }

  @Test
  @SetEnvironmentVariable(
      key = "_X_AMZN_TRACE_ID",
      value = "Root=1-8a3c60f7-d188f8fa79d48a391a778fa6;Parent=0000000000000456;Sampled=1")
  void handlerLinksToInfrastructureTrace() {
    String result = handler().handleRequest("hello", context);
    assertThat(result).isEqualTo("world");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("my_function")
                            .hasKind(SpanKind.SERVER)
                            .hasTraceId("8a3c60f7d188f8fa79d48a391a778fa6")
                            .hasParentSpanId("0000000000000456")
                            .hasAttributesSatisfying(
                                attrs ->
                                    assertThat(attrs)
                                        .containsOnly(
                                            entry(
                                                SemanticAttributes.FAAS_EXECUTION, "1-22-333")))));
  }
}
