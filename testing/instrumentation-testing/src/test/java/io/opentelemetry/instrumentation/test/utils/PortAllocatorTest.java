/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Closeable;
import org.junit.jupiter.api.Test;

class PortAllocatorTest {

  @Test
  void testSimple() {
    PortAllocator portAllocator = getPortAllocator((port) -> true);
    int next = PortAllocator.RANGE_MIN + 1;
    for (int i = 0; i < 1000; i++) {
      assertThat(portAllocator.getPort()).isEqualTo(next);
      next++;
      if (next % PortAllocator.CHUNK_SIZE == 0) {
        next++;
      }
    }
    assertThat(portAllocator.getPorts(10)).isEqualTo(next);
    assertThat(portAllocator.getPorts(PortAllocator.CHUNK_SIZE - 1)).isEqualTo(12101);
    assertThatThrownBy(() -> portAllocator.getPorts(PortAllocator.CHUNK_SIZE + 1))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void testEven() {
    PortAllocator portAllocator = getPortAllocator((port) -> port % 2 == 0);
    int next = PortAllocator.RANGE_MIN + 2;
    for (int i = 0; i < 1000; i++) {
      assertThat(portAllocator.getPort()).isEqualTo(next);
      next += 2;
      if (next % PortAllocator.CHUNK_SIZE == 0) {
        next += 2;
      }
    }
    assertThatThrownBy(() -> portAllocator.getPorts(2)).isInstanceOf(IllegalStateException.class);
  }

  private static PortAllocator getPortAllocator(PortTest portTest) {
    return new PortAllocator(new TestPortBinder(portTest));
  }

  private interface PortTest {
    boolean test(int port);
  }

  private static class TestPortBinder extends PortAllocator.PortBinder {
    private final PortTest portTest;

    TestPortBinder(PortTest portTest) {
      this.portTest = portTest;
    }

    @Override
    Closeable bind(int port) {
      if (canBind(port)) {
        return () -> {};
      }
      return null;
    }

    @Override
    boolean canBind(int port) {
      return portTest.test(port);
    }
  }
}
