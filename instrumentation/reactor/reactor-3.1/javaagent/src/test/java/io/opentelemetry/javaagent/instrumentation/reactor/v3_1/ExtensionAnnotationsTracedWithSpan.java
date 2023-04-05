/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_1;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings("deprecation") // testing instrumentation of deprecated class
public class ExtensionAnnotationsTracedWithSpan implements TracedWithSpan {

  @Override
  @io.opentelemetry.extension.annotations.WithSpan("TracedWithSpan.mono")
  public Mono<String> mono(Mono<String> mono) {
    return mono;
  }

  @Override
  @io.opentelemetry.extension.annotations.WithSpan("TracedWithSpan.outer")
  public Mono<String> outer(Mono<String> inner) {
    return mono(inner);
  }

  @Override
  @io.opentelemetry.extension.annotations.WithSpan("TracedWithSpan.flux")
  public Flux<String> flux(Flux<String> flux) {
    return flux;
  }
}
