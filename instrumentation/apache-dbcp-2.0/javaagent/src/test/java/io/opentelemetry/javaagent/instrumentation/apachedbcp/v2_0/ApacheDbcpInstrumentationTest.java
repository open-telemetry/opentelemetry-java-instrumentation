/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.apachedbcp.AbstractApacheDbcpInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ApacheDbcpInstrumentationTest extends AbstractApacheDbcpInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(BasicDataSource dataSource, String dataSourceName) {
    dataSource.setJmxName(dataSourceName);
  }

  @Override
  protected void shutdown(BasicDataSource dataSource) {
    dataSource.postDeregister();
  }

  @Test
  void shouldReportMetricsWithDefaultDataSourceNameWhenJmxNameIsNull() throws Exception {
    BasicDataSource dataSource = createDataSource();

    try {
      dataSource.getConnection().close();

      assertConnectionUsagePoolNamesSatisfying(
          poolNames ->
              assertThat(poolNames)
                  .hasSize(1)
                  .allMatch(poolName -> poolName != null && poolName.matches("dbcp2-[0-9]+")));
    } finally {
      dataSource.close();
      shutdown(dataSource);
    }

    assertNoMetrics();
  }

  @Test
  void shouldGenerateDifferentDefaultDataSourceNames() throws Exception {
    BasicDataSource firstDataSource = createDataSource();
    BasicDataSource secondDataSource = createDataSource();

    try {
      firstDataSource.getConnection().close();
      secondDataSource.getConnection().close();

      assertConnectionUsagePoolNamesSatisfying(
          poolNames ->
              assertThat(poolNames)
                  .hasSize(2)
                  .allMatch(poolName -> poolName != null && poolName.matches("dbcp2-[0-9]+")));
    } finally {
      firstDataSource.close();
      secondDataSource.close();
      shutdown(firstDataSource);
      shutdown(secondDataSource);
    }

    assertNoMetrics();
  }

  @Test
  void shouldPreferJmxNameOverRegisteredJmxName() throws Exception {
    BasicDataSource dataSource = createDataSource();
    dataSource.setJmxName("configuredPool");

    ObjectName objectName =
        new ObjectName("org.apache.commons.dbcp2:type=BasicDataSource,name=registeredPool");
    MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    mBeanServer.registerMBean(dataSource, objectName);

    try {
      dataSource.getConnection().close();

      assertDataSourceMetrics("configuredPool");
    } finally {
      dataSource.close();
      if (mBeanServer.isRegistered(objectName)) {
        mBeanServer.unregisterMBean(objectName);
      }
      shutdown(dataSource);
    }

    assertNoMetrics();
  }

  @Test
  void shouldUseRegisteredJmxNameWhenJmxNameIsNull() throws Exception {
    BasicDataSource dataSource = createDataSource();

    ObjectName objectName =
        new ObjectName("org.apache.commons.dbcp2:type=BasicDataSource,name=registeredPoolFallback");
    MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    mBeanServer.registerMBean(dataSource, objectName);

    try {
      dataSource.getConnection().close();

      assertDataSourceMetrics("registeredPoolFallback");
    } finally {
      dataSource.close();
      if (mBeanServer.isRegistered(objectName)) {
        mBeanServer.unregisterMBean(objectName);
      }
      shutdown(dataSource);
    }

    assertNoMetrics();
  }
}
