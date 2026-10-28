/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.jaxrs.AbstractJaxRsHttpServerTest;
import io.opentelemetry.instrumentation.jaxrs.v2_0.test.JaxRsTestApplication.CovariantInterfaceResource;
import io.opentelemetry.instrumentation.jaxrs.v2_0.test.JaxRsTestApplication.CovariantSuperclassResource;
import io.opentelemetry.instrumentation.jaxrs.v2_0.test.JaxRsTestResource;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class JaxRsHttpServerTest<SERVER> extends AbstractJaxRsHttpServerTest<SERVER> {

  private static final boolean CONTROLLER_TELEMETRY_ENABLED =
      Boolean.getBoolean("otel.instrumentation.common.controller-telemetry.enabled");

  @BeforeEach
  void setup() {
    // reset the barrier to avoid a failing test breaking subsequent tests
    JaxRsTestResource.resetBarrier();
  }

  @Override
  protected void awaitBarrier(int amount, TimeUnit timeUnit)
      throws BrokenBarrierException, InterruptedException, TimeoutException {
    JaxRsTestResource.awaitBarrier(amount, timeUnit);
  }

  @ParameterizedTest
  @MethodSource("covariantResources")
  void covariantResourceMethod(String path, Class<?> resourceClass) {
    AggregatedHttpResponse response =
        client.get(h1Address.resolve(path).toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("item");

    testing()
        .waitAndAssertTraces(
            trace -> {
              Consumer<SpanDataAssert> serverSpan =
                  span ->
                      assertServerSpan(
                              span,
                              "GET",
                              new ServerEndpoint("covariant", path, 200, "item", false),
                              200)
                          .hasNoParent();
              if (CONTROLLER_TELEMETRY_ENABLED) {
                trace.hasSpansSatisfyingExactly(
                    serverSpan,
                    span ->
                        span.hasName(resourceClass.getSimpleName() + ".get")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(CODE_FUNCTION_NAME, resourceClass.getName() + ".get")));
              } else {
                trace.hasSpansSatisfyingExactly(serverSpan);
              }
            });
  }

  private static Stream<Arguments> covariantResources() {
    return Stream.of(
        Arguments.of("covariant-interface/current", CovariantInterfaceResource.class),
        Arguments.of("covariant-superclass/current", CovariantSuperclassResource.class));
  }
}
