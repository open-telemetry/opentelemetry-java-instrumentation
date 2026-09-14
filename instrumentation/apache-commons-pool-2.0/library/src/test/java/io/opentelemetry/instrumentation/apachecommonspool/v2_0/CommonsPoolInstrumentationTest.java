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
  void shouldRegisterSamePoolInstanceIdempotently() throws Exception {
    String poolName = "duplicateRegistrationPool";
    String ignoredPoolName = "ignoredDuplicateRegistrationPool";
    GenericObjectPool<Object> pool = createGenericObjectPool(poolName, false);
    Object borrowed = null;
    try {
      configure(pool, poolName);
      configure(pool, ignoredPoolName);

      borrowed = pool.borrowObject();

      assertGenericObjectPoolMetrics(poolName);
      verifyPoolNameNotReported(ignoredPoolName);
    } finally {
      if (borrowed != null) {
        pool.returnObject(borrowed);
      }
      shutdown(pool);
      pool.close();
    }

    assertNoMetrics();
  }
}
