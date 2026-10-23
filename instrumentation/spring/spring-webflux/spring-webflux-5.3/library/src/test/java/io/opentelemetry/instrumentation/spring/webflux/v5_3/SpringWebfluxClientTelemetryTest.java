/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

class SpringWebfluxClientTelemetryTest {

  @BeforeEach
  @AfterEach
  void resetReactorHook() {
    ContextPropagationOperator.create().resetOnEachOperator();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void registersReactorHookOnlyWhenRequested(boolean registerHook) {
    SpringWebfluxClientTelemetry telemetry =
        SpringWebfluxClientTelemetry.create(OpenTelemetry.noop());
    if (registerHook) {
      telemetry.createFilterAndRegisterReactorHook();
    } else {
      telemetry.createFilter();
    }

    ContextKey<String> key = ContextKey.named("test");
    Mono<Boolean> hasContext =
        Mono.just(1).map(unused -> "value".equals(Context.current().get(key)));
    assertThat(
            ContextPropagationOperator.runWithContext(hasContext, Context.root().with(key, "value"))
                .block())
        .isEqualTo(registerHook);
  }
}
