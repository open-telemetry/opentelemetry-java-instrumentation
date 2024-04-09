/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdacore.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.api.client.AwsLambdaInternalRequestHandler;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AbstractAwsLambdaTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.incubating.FaasIncubatingAttributes;
import lambdainternal.AwsLambdaLegacyInternalRequestHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AwsLambdaTest extends AbstractAwsLambdaTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected RequestHandler<String, String> handler() {
    return new TestRequestHandler();
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @AfterEach
  void tearDown() {
    assertThat(testing.forceFlushCalled()).isTrue();
  }

  @Test
  void awsLambdaInternalHandlerIgnoredAndUserHandlerTraced() {
    RequestHandler<String, String> handler = new AwsLambdaInternalRequestHandler(handler());
    String result = handler.handleRequest("hello", context());
    assertThat(result).isEqualTo("world");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("my_function")
                            .hasKind(SpanKind.SERVER)
                            .hasAttributesSatisfyingExactly(
                                equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333"))));
  }

  @Test
  void awsLambdaLegacyInternalHandlerIgnoredAndUserHandlerTraced() {
    RequestHandler<String, String> handler = new AwsLambdaLegacyInternalRequestHandler(handler());
    String result = handler.handleRequest("hello", context());
    assertThat(result).isEqualTo("world");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("my_function")
                            .hasKind(SpanKind.SERVER)
                            .hasAttributesSatisfyingExactly(
                                equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333"))));
  }

  private static final class TestRequestHandler implements RequestHandler<String, String> {

    @Override
    public String handleRequest(String input, Context context) {
      return doHandleRequest(input, context);
    }
  }
}
