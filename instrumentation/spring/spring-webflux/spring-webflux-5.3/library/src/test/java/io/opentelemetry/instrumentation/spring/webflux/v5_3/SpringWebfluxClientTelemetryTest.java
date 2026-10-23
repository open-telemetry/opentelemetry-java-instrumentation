/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class SpringWebfluxClientTelemetryTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @BeforeEach
  @AfterEach
  void resetReactorHook() {
    ContextPropagationOperator.create().resetOnEachOperator();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void createsFilterWithOptionalReactorHook(boolean registerHook) {
    SpringWebfluxClientTelemetry telemetry =
        SpringWebfluxClientTelemetry.create(testing.getOpenTelemetry());
    ExchangeFilterFunction filter =
        registerHook ? telemetry.createFilterAndRegisterReactorHook() : telemetry.createFilter();

    ContextKey<String> key = ContextKey.named("test");
    Mono<Boolean> hasContext =
        Mono.just(1).map(unused -> "value".equals(Context.current().get(key)));
    assertThat(
            ContextPropagationOperator.runWithContext(hasContext, Context.root().with(key, "value"))
                .block())
        .isEqualTo(registerHook);

    WebClient client =
        WebClient.builder()
            .filter(filter)
            .exchangeFunction(
                request -> {
                  assertThat(request.headers().getFirst("traceparent")).isNotNull();
                  return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
            .build();

    client.get().uri("http://localhost/test").retrieve().toBodilessEntity().block();

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT)));
  }
}
