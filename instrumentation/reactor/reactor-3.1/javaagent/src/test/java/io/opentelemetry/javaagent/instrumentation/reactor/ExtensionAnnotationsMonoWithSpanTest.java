/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor;

import io.opentelemetry.javaagent.instrumentation.otelannotations.AbstractTraced;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;

@SuppressWarnings("deprecation") // testing instrumentation of deprecated class
class ExtensionAnnotationsMonoWithSpanTest extends BaseMonoWithSpanTest {

  @Override
  protected AbstractTraced<Mono<String>, Mono<String>> newTraced() {
    return new Traced();
  }

  @Override
  TracedWithSpan newTracedWithSpan() {
    return new ExtensionAnnotationsTracedWithSpan();
  }

  static class Traced extends AbstractTraced<Mono<String>, Mono<String>> {

    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    protected Mono<String> completable() {
      UnicastProcessor<String> source = UnicastProcessor.create();
      return source.singleOrEmpty();
    }

    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    protected Mono<String> alreadySucceeded() {
      return Mono.just(SUCCESS_VALUE);
    }

    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    protected Mono<String> alreadyFailed() {
      return Mono.error(FAILURE);
    }
  }
}
