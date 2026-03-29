/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.cassandra.v4_4.AbstractCassandra44Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraTest extends AbstractCassandra44Test {

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.cassandra-4.4";
  }

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
