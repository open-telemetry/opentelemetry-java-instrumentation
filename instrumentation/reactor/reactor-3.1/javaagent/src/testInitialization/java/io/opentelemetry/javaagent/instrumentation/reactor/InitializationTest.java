/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.extension.annotations.WithSpan;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import reactor.core.Scannable;
import reactor.core.publisher.Mono;

// Isolated test to use clean classloader because reactor instrumentation is applied on static
// initialization.
class InitializationTest {

  @Test
  void contextPropagated() {
    Mono<String> mono = new Traced().traceMe();

    // If reactor augmentation of WithSpan is working correctly, we will end up with these
    // implementation details.
    // TODO(anuraaga): This should just check actual context propagation instead of implementation
    // but couldn't figure out how.
    assertThat(((Scannable) mono).parents().collect(Collectors.toList()))
        .anySatisfy(
            op -> {
              assertThat(op.getClass().getSimpleName()).isEqualTo("MonoFlatMap");
              assertThat(op)
                  .extracting("source")
                  .satisfies(
                      source ->
                          assertThat(source.getClass().getSimpleName())
                              .isEqualTo("ScalarPropagatingMono"));
            });

    assertThat(mono.block()).isEqualTo("foo");
  }

  static class Traced {
    @WithSpan
    Mono<String> traceMe() {
      return Mono.just("foo");
    }
  }
}
