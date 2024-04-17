/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor.v3_4;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ContextViewTest {

  static final Object TRACE_CONTEXT_KEY;

  static {
    try {
      Field field =
          io.opentelemetry.instrumentation.reactor.v3.common.ContextPropagationOperator.class
              .getDeclaredField("TRACE_CONTEXT_KEY");
      field.setAccessible(true);
      TRACE_CONTEXT_KEY = field.get(null);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testContextWrite() {
    Mono<String> r =
        Mono.just("Hello")
            .flatMap(
                s -> Mono.deferContextual(ctx -> Mono.just(s + " " + ctx.get(TRACE_CONTEXT_KEY))))
            .contextWrite(ctx -> ctx.put(TRACE_CONTEXT_KEY, "World"));

    StepVerifier.create(r).expectNext("Hello World").verifyComplete();
  }
}
