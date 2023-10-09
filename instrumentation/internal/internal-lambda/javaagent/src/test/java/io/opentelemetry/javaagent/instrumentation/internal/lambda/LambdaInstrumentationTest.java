/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker;
import org.junit.jupiter.api.Test;

class LambdaInstrumentationTest {

  @Test
  void testTransformRunnableLambda() {
    Runnable runnable = TestLambda.makeRunnable();

    // RunnableInstrumentation adds a VirtualField to all implementors of Runnable. If lambda class
    // is transformed then it must have context store marker interface.
    assertThat(runnable).isInstanceOf(VirtualFieldInstalledMarker.class);
    assertThat(VirtualFieldInstalledMarker.class.isAssignableFrom(Runnable.class)).isFalse();
  }
}
