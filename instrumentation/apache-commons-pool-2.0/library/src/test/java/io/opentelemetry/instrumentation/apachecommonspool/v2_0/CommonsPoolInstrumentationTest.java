/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachecommonspool.v2_0;

import io.opentelemetry.instrumentation.apachecommonspool.AbstractCommonsPoolInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CommonsPoolInstrumentationTest extends AbstractCommonsPoolInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static CommonsPoolTelemetry telemetry;

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @BeforeAll
  static void setup() {
    telemetry = CommonsPoolTelemetry.create(testing.getOpenTelemetry());
  }

  @Override
  protected void configure(GenericObjectPool<?> pool, String poolName) {
    telemetry.registerMetrics(pool, poolName);
  }

  @Override
  protected void configure(GenericKeyedObjectPool<?, ?> pool, String poolName) {
    telemetry.registerMetrics(pool, poolName);
  }

  @Override
  protected void shutdown(GenericObjectPool<?> pool) {
    telemetry.unregisterMetrics(pool);
  }

  @Override
  protected void shutdown(GenericKeyedObjectPool<?, ?> pool) {
    telemetry.unregisterMetrics(pool);
  }

  @Test
  void shouldSkipReservedPoolNameSuffix() throws Exception {
    GenericObjectPool<Object> first = createGenericObjectPool("reservedPool", false);
    GenericObjectPool<Object> reserved = createGenericObjectPool("reservedPool-2", false);
    GenericObjectPool<Object> second = createGenericObjectPool("reservedPool", false);
    Object firstBorrowed = null;
    Object reservedBorrowed = null;
    Object secondBorrowed = null;
    try {
      String poolName = "GenericObjectPool-reservedPool";
      telemetry.registerMetrics(first, poolName);
      telemetry.registerMetrics(reserved, poolName + "-2");
      telemetry.registerMetrics(second, poolName);

      firstBorrowed = first.borrowObject();
      reservedBorrowed = reserved.borrowObject();
      secondBorrowed = second.borrowObject();

      assertObjectCountPoolNames(poolName, poolName + "-2", poolName + "-3");
    } finally {
      if (firstBorrowed != null) {
        first.returnObject(firstBorrowed);
      }
      if (reservedBorrowed != null) {
        reserved.returnObject(reservedBorrowed);
      }
      if (secondBorrowed != null) {
        second.returnObject(secondBorrowed);
      }
      telemetry.unregisterMetrics(first);
      telemetry.unregisterMetrics(reserved);
      telemetry.unregisterMetrics(second);
      first.close();
      reserved.close();
      second.close();
    }

    assertNoMetrics();
  }
}
