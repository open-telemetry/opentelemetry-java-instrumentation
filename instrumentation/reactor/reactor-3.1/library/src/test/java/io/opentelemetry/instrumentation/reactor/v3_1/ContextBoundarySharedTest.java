/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor.v3_1;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.AbstractContextBoundaryTest;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ContextBoundarySharedTest extends AbstractContextBoundaryTest {

  private final ContextPropagationOperator operator = ContextPropagationOperator.create();

  @Override
  @SuppressWarnings("unchecked")
  protected Publisher<String> runWithContext(
      Publisher<String> source, Context operationContext, Context applicationContext) {
    if (source instanceof Mono) {
      return ContextPropagationOperator.runWithContext(
          (Mono<String>) source, operationContext, applicationContext);
    }
    return ContextPropagationOperator.runWithContext(
        (Flux<String>) source, operationContext, applicationContext);
  }

  @Override
  protected void registerHooks() {
    operator.registerOnEachOperator();
  }

  @Override
  protected void resetHooks() {
    operator.resetOnEachOperator();
  }
}
