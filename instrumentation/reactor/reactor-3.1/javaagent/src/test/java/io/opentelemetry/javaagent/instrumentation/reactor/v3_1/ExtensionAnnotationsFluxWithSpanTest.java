/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_1;

import io.opentelemetry.javaagent.instrumentation.otelannotations.AbstractTraced;
import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;

@SuppressWarnings("deprecation") // testing instrumentation of deprecated class
class ExtensionAnnotationsFluxWithSpanTest extends BaseFluxWithSpanTest {

  @Override
  protected AbstractTraced<Flux<String>, Flux<String>> newTraced() {
    return new Traced();
  }

  @Override
  TracedWithSpan newTracedWithSpan() {
    return new ExtensionAnnotationsTracedWithSpan();
  }

  static class Traced extends AbstractTraced<Flux<String>, Flux<String>> {

    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    protected Flux<String> completable() {
      return UnicastProcessor.create();
    }

    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    protected Flux<String> alreadySucceeded() {
      return Flux.just(SUCCESS_VALUE);
    }

    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    protected Flux<String> alreadyFailed() {
      return Flux.error(FAILURE);
    }
  }
}
