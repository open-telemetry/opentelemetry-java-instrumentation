/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import io.opentelemetry.extension.annotations.WithSpan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TracedWithSpan {
  @WithSpan
  public Mono<String> mono(Mono<String> mono) {
    return mono;
  }

  @WithSpan
  public Mono<String> outer(Mono<String> inner) {
    return mono(inner);
  }

  @WithSpan
  public Flux<String> flux(Flux<String> flux) {
    return flux;
  }
}
