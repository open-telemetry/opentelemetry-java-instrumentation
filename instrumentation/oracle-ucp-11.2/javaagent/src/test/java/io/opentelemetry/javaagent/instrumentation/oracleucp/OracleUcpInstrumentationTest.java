/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oracleucp;

import io.opentelemetry.instrumentation.oracleucp.AbstractOracleUcpInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import oracle.ucp.jdbc.PoolDataSource;
import org.junit.jupiter.api.extension.RegisterExtension;

class OracleUcpInstrumentationTest extends AbstractOracleUcpInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(PoolDataSource connectionPool) {}

  @Override
  protected void shutdown(PoolDataSource connectionPool) {}
}
