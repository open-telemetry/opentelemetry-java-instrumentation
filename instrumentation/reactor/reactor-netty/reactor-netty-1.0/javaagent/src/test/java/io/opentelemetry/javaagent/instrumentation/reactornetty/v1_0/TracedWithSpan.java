/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import reactor.core.publisher.Mono;

public class TracedWithSpan {

  @WithSpan
  public <T> Mono<T> mono(Mono<T> mono) {
    return mono;
  }
}
