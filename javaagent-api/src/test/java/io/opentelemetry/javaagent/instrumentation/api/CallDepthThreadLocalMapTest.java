/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CallDepthThreadLocalMapTest {

  @Test
  void incrementDecrement() {
    assertThat(CallDepthThreadLocalMap.incrementCallDepth(String.class)).isZero();
    assertThat(CallDepthThreadLocalMap.incrementCallDepth(Integer.class)).isZero();

    assertThat(CallDepthThreadLocalMap.incrementCallDepth(String.class)).isOne();
    assertThat(CallDepthThreadLocalMap.incrementCallDepth(Integer.class)).isOne();

    CallDepthThreadLocalMap.reset(String.class);
    assertThat(CallDepthThreadLocalMap.incrementCallDepth(Integer.class)).isEqualTo(2);

    CallDepthThreadLocalMap.reset(Integer.class);

    assertThat(CallDepthThreadLocalMap.incrementCallDepth(String.class)).isZero();
    assertThat(CallDepthThreadLocalMap.incrementCallDepth(Integer.class)).isZero();

    assertThat(CallDepthThreadLocalMap.incrementCallDepth(String.class)).isOne();
    assertThat(CallDepthThreadLocalMap.incrementCallDepth(Integer.class)).isOne();

    assertThat(CallDepthThreadLocalMap.decrementCallDepth(String.class)).isOne();
    assertThat(CallDepthThreadLocalMap.decrementCallDepth(Integer.class)).isOne();

    assertThat(CallDepthThreadLocalMap.decrementCallDepth(String.class)).isZero();
    assertThat(CallDepthThreadLocalMap.decrementCallDepth(Integer.class)).isZero();

    assertThat(CallDepthThreadLocalMap.incrementCallDepth(Double.class)).isZero();
    assertThat(CallDepthThreadLocalMap.decrementCallDepth(Double.class)).isZero();
  }
}
