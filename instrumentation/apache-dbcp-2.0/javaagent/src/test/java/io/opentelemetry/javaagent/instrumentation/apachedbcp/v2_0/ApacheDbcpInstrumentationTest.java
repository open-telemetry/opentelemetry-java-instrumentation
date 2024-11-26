/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0;

import io.opentelemetry.instrumentation.apachedbcp.AbstractApacheDbcpInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.management.ManagementFactory;
import javax.management.ObjectName;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.extension.RegisterExtension;

class ApacheDbcpInstrumentationTest extends AbstractApacheDbcpInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(BasicDataSource dataSource, String dataSourceName) throws Exception {
    dataSource.preRegister(
        ManagementFactory.getPlatformMBeanServer(),
        new ObjectName("io.opentelemetry.db:name=" + dataSourceName));
  }

  @Override
  protected void shutdown(BasicDataSource dataSource) {
    dataSource.postDeregister();
  }
}
