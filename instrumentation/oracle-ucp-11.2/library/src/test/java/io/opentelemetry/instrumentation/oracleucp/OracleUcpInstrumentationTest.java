/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oracleucp;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import oracle.ucp.UniversalConnectionPool;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

public class OracleUcpInstrumentationTest extends AbstractOracleUcpInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static OracleUcpTelemetry telemetry;

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @BeforeAll
  static void setup() {
    telemetry = OracleUcpTelemetry.create(testing.getOpenTelemetry());
  }

  @Override
  protected void configure(PoolDataSource connectionPool) throws Exception {
    UniversalConnectionPool universalConnectionPool =
        UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager()
            .getConnectionPool(connectionPool.getConnectionPoolName());
    telemetry.registerMetrics(universalConnectionPool);
  }

  @Override
  protected void shutdown(PoolDataSource connectionPool) throws Exception {
    UniversalConnectionPool universalConnectionPool =
        UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager()
            .getConnectionPool(connectionPool.getConnectionPoolName());
    telemetry.unregisterMetrics(universalConnectionPool);
  }
}
