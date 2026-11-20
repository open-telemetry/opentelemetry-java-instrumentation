/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationCallbackHandleTest {

  @Test
  void releasesOnceEvenWhenDelegateCloseThrows() {
    List<String> calls = new ArrayList<>();
    RuntimeException failure = new RuntimeException("close failed");
    ApplicationCallbackHandle handle =
        new ApplicationCallbackHandle(
            () -> {
              calls.add("close");
              throw failure;
            },
            () -> calls.add("release"));

    assertThatThrownBy(handle::close).isSameAs(failure);
    handle.close();
    assertThat(calls).containsExactly("close", "release");
  }
}
