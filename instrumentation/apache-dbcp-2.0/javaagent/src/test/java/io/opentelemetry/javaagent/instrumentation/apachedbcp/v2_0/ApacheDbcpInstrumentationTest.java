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
  private static final String COMMONS_POOL_INSTRUMENTATION_NAME =
      "io.opentelemetry.apache-commons-pool-2.0";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(BasicDataSource dataSource, String dataSourceName) {
    dataSource.setJmxName("org.apache.commons.dbcp2:type=BasicDataSource,name=" + dataSourceName);
  }

  @Test
  void shouldUseJdbcUrlForDataSourceNameWhenJmxNameIsNull() throws Exception {
    BasicDataSource dataSource = createDataSource();
    dataSource.setUrl("jdbc:postgresql://db.example:5432/orders");

    assertDataSourceName(dataSource, "db.example:5432/orders");
  }

  @Test
  void shouldBracketIpv6AddressInDataSourceName() throws Exception {
    BasicDataSource dataSource = createDataSource();
    dataSource.setUrl("jdbc:postgresql://[2001:db8::1]:5432/orders");

    assertDataSourceName(dataSource, "[2001:db8::1]:5432/orders");
  }

  @Test
  void shouldUseConnectionPropertiesForDataSourceNameWhenJmxNameIsInvalid() throws Exception {
    BasicDataSource dataSource = createDataSource();
    dataSource.setJmxName("invalid-jmx-name");
    dataSource.setUrl("jdbc:postgresql:ignored");
    dataSource.addConnectionProperty("serverName", "properties.example");
    dataSource.addConnectionProperty("portNumber", "5433");
    dataSource.addConnectionProperty("databaseName", "inventory");

    assertDataSourceName(dataSource, "properties.example:5433/inventory");
  }

  @Test
  void shouldUseServerAddressWhenPortAndNamespaceAreMissing() throws Exception {
    BasicDataSource dataSource = createDataSource();
    dataSource.setUrl("jdbc:custom:ignored");
    dataSource.addConnectionProperty("serverName", "address-only.example");

    assertDataSourceName(dataSource, "address-only.example");
  }

  @Test
  void shouldUseDbNamespaceWhenServerAddressIsMissing() throws Exception {
    BasicDataSource dataSource = createDataSource();
    dataSource.setUrl("jdbc:h2:mem:orders");

    assertDataSourceName(dataSource, "orders");
  }

  @Test
  void shouldUseFixedDataSourceNameWhenServerAddressAndNamespaceAreMissing() throws Exception {
    BasicDataSource dataSource = createDataSource();

    assertDataSourceName(dataSource, "apache-dbcp2");
  }

  @Test
  void shouldNotReportCommonsPoolMetrics() throws Exception {
    BasicDataSource dataSource = createDataSource();

    try {
      dataSource.getConnection().close();

      assertDataSourceMetrics("apache-dbcp2");
      assertNoCommonsPoolMetrics();
    } finally {
      dataSource.close();
    }

    assertNoMetrics();
  }

  @Test
  void shouldPreferJmxNameOverRegisteredJmxName() throws Exception {
    BasicDataSource dataSource = createDataSource();
    dataSource.setJmxName("org.apache.commons.dbcp2:type=BasicDataSource,name=configuredPool");

    ObjectName objectName =
        new ObjectName("org.apache.commons.dbcp2:type=BasicDataSource,name=registeredPool");
    MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
    objectName = mbeanServer.registerMBean(dataSource, objectName).getObjectName();

    try {
      dataSource.getConnection().close();

      assertDataSourceMetrics("configuredPool");
    } finally {
      dataSource.close();
      if (mbeanServer.isRegistered(objectName)) {
        mbeanServer.unregisterMBean(objectName);
      }
    }

    assertNoMetrics();
  }

  @Test
  void shouldUseRegisteredJmxNameWhenJmxNameIsNull() throws Exception {
    BasicDataSource dataSource = createDataSource();

    ObjectName objectName =
        new ObjectName("org.apache.commons.dbcp2:type=BasicDataSource,name=registeredPoolFallback");
    MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
    mbeanServer.registerMBean(dataSource, objectName);

    try {
      dataSource.getConnection().close();

      assertDataSourceMetrics("registeredPoolFallback");
    } finally {
      dataSource.close();
      if (mbeanServer.isRegistered(objectName)) {
        mbeanServer.unregisterMBean(objectName);
      }
    }

    assertNoMetrics();
  }

  @Test
  void shouldUpdateDataSourceNameWhenMBeanIsRegisteredAfterPoolStart() throws Exception {
    BasicDataSource dataSource = createDataSource();
    dataSource.setUrl("jdbc:postgresql://db.example:5432/orders");
    ObjectName objectName =
        new ObjectName("org.apache.commons.dbcp2:type=BasicDataSource,name=lateRegisteredPool");
    MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

    try {
      dataSource.getConnection().close();
      assertDataSourceMetrics("db.example:5432/orders");

      objectName = mbeanServer.registerMBean(dataSource, objectName).getObjectName();
      assertDataSourceMetrics("lateRegisteredPool");
    } finally {
      dataSource.close();
      if (mbeanServer.isRegistered(objectName)) {
        mbeanServer.unregisterMBean(objectName);
      }
    }

    assertNoMetrics();
  }

  @Test
  void shouldReportMetricsAfterMBeanDeregistration() throws Exception {
    BasicDataSource dataSource = createDataSource();

    ObjectName objectName =
        new ObjectName("org.apache.commons.dbcp2:type=BasicDataSource,name=deregisteredPool");
    MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
    objectName = mbeanServer.registerMBean(dataSource, objectName).getObjectName();

    try {
      dataSource.getConnection().close();
      mbeanServer.unregisterMBean(objectName);

      assertDataSourceMetrics("deregisteredPool");
    } finally {
      dataSource.close();
      if (mbeanServer.isRegistered(objectName)) {
        mbeanServer.unregisterMBean(objectName);
      }
    }

    assertNoMetrics();
  }

  private void assertDataSourceName(BasicDataSource dataSource, String dataSourceName)
      throws Exception {
    try {
      dataSource.getConnection().close();

      assertDataSourceMetrics(dataSourceName);
    } finally {
      dataSource.close();
    }

    assertNoMetrics();
  }

  private static void assertNoCommonsPoolMetrics() {
    assertThat(testing.metrics())
        .filteredOn(
            metricData ->
                metricData
                    .getInstrumentationScopeInfo()
                    .getName()
                    .equals(COMMONS_POOL_INSTRUMENTATION_NAME))
        .isEmpty();
  }
}
