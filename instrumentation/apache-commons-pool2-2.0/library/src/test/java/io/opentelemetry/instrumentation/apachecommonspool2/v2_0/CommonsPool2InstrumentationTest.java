/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachecommonspool2.v2_0;

import io.opentelemetry.instrumentation.apachecommonspool2.AbstractCommonsPool2InstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class CommonsPool2InstrumentationTest extends AbstractCommonsPool2InstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static CommonsPool2Telemetry telemetry;

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @BeforeAll
  static void setup() {
    telemetry = CommonsPool2Telemetry.create(testing.getOpenTelemetry());
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
}
