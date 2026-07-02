/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecommonspool.v2_0;

import io.opentelemetry.instrumentation.apachecommonspool.AbstractCommonsPoolInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CommonsPoolInstrumentationTest extends AbstractCommonsPoolInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(GenericObjectPool<?> pool, String poolName) {}

  @Override
  protected void configure(GenericKeyedObjectPool<?, ?> pool, String poolName) {}

  @Override
  protected void shutdown(GenericObjectPool<?> pool) {}

  @Override
  protected void shutdown(GenericKeyedObjectPool<?, ?> pool) {}

  @Test
  void shouldUseJmxNamePrefixWhenJmxNameIsUnavailable() throws Exception {
    GenericObjectPool<Object> pool = createGenericObjectPool("customPool", false);
    Object borrowed = null;
    try {
      borrowed = pool.borrowObject();

      assertObjectCountPoolNames("GenericObjectPool-customPool");
    } finally {
      if (borrowed != null) {
        pool.returnObject(borrowed);
      }
      pool.close();
    }

    assertNoMetrics();
  }

  @Test
  void shouldUseUnknownWhenJmxNameAndPrefixAreUnavailable() throws Exception {
    GenericObjectPool<Object> pool = createGenericObjectPool(null, false);
    Object borrowed = null;
    try {
      borrowed = pool.borrowObject();

      assertObjectCountPoolNames("GenericObjectPool-unknown");
    } finally {
      if (borrowed != null) {
        pool.returnObject(borrowed);
      }
      pool.close();
    }

    assertNoMetrics();
  }
}
