/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Resilience4jCircuitBreakerInstrumentationModule extends InstrumentationModule {

  public Resilience4jCircuitBreakerInstrumentationModule() {
    super("resilience4j-circuitbreaker", "resilience4j-circuitbreaker-0.15");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new CircuitBreakerDecoratorsInstrumentation(),
        new CircuitBreakerStateMachineInstrumentation());
  }
}
