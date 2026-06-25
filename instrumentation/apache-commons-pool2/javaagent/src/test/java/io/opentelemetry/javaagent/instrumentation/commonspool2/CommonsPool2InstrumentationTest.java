/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.commonspool2;

import io.opentelemetry.instrumentation.commonspool2.AbstractCommonsPool2InstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.jupiter.api.extension.RegisterExtension;

class CommonsPool2InstrumentationTest extends AbstractCommonsPool2InstrumentationTest {

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
}
