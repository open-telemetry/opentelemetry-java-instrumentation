/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class InstrumentationAnnotationsTracedWithSpan implements TracedWithSpan {

  @Override
  @WithSpan("TracedWithSpan.mono")
  public Mono<String> mono(Mono<String> mono) {
    return mono;
  }

  @Override
  @WithSpan("TracedWithSpan.outer")
  public Mono<String> outer(Mono<String> inner) {
    return mono(inner);
  }

  @Override
  @WithSpan("TracedWithSpan.flux")
  public Flux<String> flux(Flux<String> flux) {
    return flux;
  }
}
