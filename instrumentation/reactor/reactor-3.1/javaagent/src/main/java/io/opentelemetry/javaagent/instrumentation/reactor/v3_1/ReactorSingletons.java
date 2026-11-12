/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;

final class ReactorSingletons {

  // A single operator, so that registering again after a reset reuses the same async operation end
  // strategy instead of adding a new one to the strategy registry each time.
  private static final ContextPropagationOperator operator =
      ContextPropagationOperator.builder()
          .setCaptureExperimentalSpanAttributes(
              DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "reactor")
                  .getBoolean("experimental_span_attributes/development", false))
          .build();

  static void registerOnEachOperator() {
    operator.registerOnEachOperator();
  }

  // Hooks.resetOnEachOperator() removes the hook without going through the operator, which still
  // considers it registered and would ignore a plain registration.
  static void reregisterOnEachOperator() {
    operator.resetOnEachOperator();
    operator.registerOnEachOperator();
  }

  private ReactorSingletons() {}
}
