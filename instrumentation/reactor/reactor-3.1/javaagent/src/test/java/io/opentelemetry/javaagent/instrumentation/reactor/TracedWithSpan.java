/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TracedWithSpan {
  Mono<String> mono(Mono<String> mono);

  Mono<String> outer(Mono<String> inner);

  Flux<String> flux(Flux<String> flux);
}
