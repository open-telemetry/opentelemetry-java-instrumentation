/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CallDepthTest {

  @Test
  void incrementDecrement() {
    assertThat(CallDepth.forClass(String.class).getAndIncrement()).isZero();
    assertThat(CallDepth.forClass(Integer.class).getAndIncrement()).isZero();

    assertThat(CallDepth.forClass(String.class).getAndIncrement()).isOne();
    assertThat(CallDepth.forClass(Integer.class).getAndIncrement()).isOne();

    assertThat(CallDepth.forClass(String.class).decrementAndGet()).isOne();
    assertThat(CallDepth.forClass(Integer.class).decrementAndGet()).isOne();

    assertThat(CallDepth.forClass(String.class).decrementAndGet()).isZero();
    assertThat(CallDepth.forClass(Integer.class).decrementAndGet()).isZero();

    assertThat(CallDepth.forClass(Double.class).getAndIncrement()).isZero();
    assertThat(CallDepth.forClass(Double.class).decrementAndGet()).isZero();
  }
}
