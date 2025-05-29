/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedbcp.v2_0;

import io.opentelemetry.instrumentation.apachedbcp.AbstractApacheDbcpInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class ApacheDbcpInstrumentationTest extends AbstractApacheDbcpInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static ApacheDbcpTelemetry telemetry;

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @BeforeAll
  static void setup() {
    telemetry = ApacheDbcpTelemetry.create(testing.getOpenTelemetry());
  }

  @Override
  protected void configure(BasicDataSource dataSource, String dataSourceName) {
    telemetry.registerMetrics(dataSource, dataSourceName);
  }

  @Override
  protected void shutdown(BasicDataSource dataSource) {
    telemetry.unregisterMetrics(dataSource);
  }
}
