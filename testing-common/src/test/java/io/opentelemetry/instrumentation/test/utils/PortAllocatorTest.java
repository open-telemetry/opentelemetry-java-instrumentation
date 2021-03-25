/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils;

import java.io.Closeable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PortAllocatorTest {

  @Test
  public void testSimple() {
    PortAllocator portAllocator = getPortAllocator((port) -> true);
    int next = PortAllocator.RANGE_MIN + 1;
    for (int i = 0; i < 1000; i++) {
      Assertions.assertEquals(next, portAllocator.getPort());
      next++;
      if (next % PortAllocator.CHUNK_SIZE == 0) {
        next++;
      }
    }
    Assertions.assertEquals(next, portAllocator.getPorts(10));
    Assertions.assertEquals(12101, portAllocator.getPorts(PortAllocator.CHUNK_SIZE - 1));
    try {
      Assertions.assertEquals(next, portAllocator.getPorts(PortAllocator.CHUNK_SIZE + 1));
      Assertions.fail("should not be able to allocate more than PORT_RANGE_STEP consecutive ports");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void testEven() {
    PortAllocator portAllocator = getPortAllocator((port) -> port % 2 == 0);
    int next = PortAllocator.RANGE_MIN + 2;
    for (int i = 0; i < 1000; i++) {
      Assertions.assertEquals(next, portAllocator.getPort());
      next += 2;
      if (next % PortAllocator.CHUNK_SIZE == 0) {
        next += 2;
      }
    }
    try {
      Assertions.assertEquals(next, portAllocator.getPorts(2));
      Assertions.fail("should not be able to allocate consecutive ports");
    } catch (IllegalStateException ignored) {
    }
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
