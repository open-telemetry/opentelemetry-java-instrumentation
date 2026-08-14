/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.dbcp.v8_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.Driver;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TomcatDbcpInstrumentationTest {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.tomcat-dbcp-8.0";

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Mock private Driver driverMock;
  @Mock private Connection connectionMock;

  @Test
  void shouldReportMetrics() throws Exception {
    BasicDataSource dataSource = createDataSource();
    try {
      configure(dataSource, "dataSourceName");

      dataSource.getConnection().close();

      assertDataSourceMetrics("dataSourceName");
    } finally {
      dataSource.close();
    }

    assertNoMetrics();
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

    assertDataSourceName(dataSource, "tomcat-dbcp");
  }

  @Test
  void shouldPreferJmxNameOverRegisteredJmxName() throws Exception {
    BasicDataSource dataSource = createDataSource();
    dataSource.setJmxName("org.apache.tomcat.dbcp.dbcp2:type=BasicDataSource,name=configuredPool");

    ObjectName objectName =
        new ObjectName("org.apache.tomcat.dbcp.dbcp2:type=BasicDataSource,name=registeredPool");
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
  void shouldPreserveRegisteredJmxNameEncodingWhenJmxNameIsNull() throws Exception {
    BasicDataSource dataSource = createDataSource();
    String objectNameValue = ObjectName.quote("registered,pool \"primary\"\\replica");

    ObjectName objectName =
        new ObjectName("org.apache.tomcat.dbcp.dbcp2:type=BasicDataSource,name=" + objectNameValue);
    MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
    objectName = mbeanServer.registerMBean(dataSource, objectName).getObjectName();

    try {
      dataSource.getConnection().close();

      assertDataSourceMetrics(objectNameValue);
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
        new ObjectName(
            "org.apache.tomcat.dbcp.dbcp2:type=BasicDataSource," + "name=lateRegisteredPool");
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
        new ObjectName(
            "org.apache.tomcat.dbcp.dbcp2:type=BasicDataSource," + "name=deregisteredPool");
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

  private BasicDataSource createDataSource() throws Exception {
    when(driverMock.connect(any(), any())).thenReturn(connectionMock);
    lenient().when(connectionMock.isValid(anyInt())).thenReturn(true);

    BasicDataSource dataSource = new BasicDataSource();
    dataSource.setDriver(driverMock);
    dataSource.setUrl("db:///url");
    return dataSource;
  }

  private static void configure(BasicDataSource dataSource, String dataSourceName) {
    dataSource.setJmxName(
        "org.apache.tomcat.dbcp.dbcp2:type=BasicDataSource,name=" + dataSourceName);
  }

  private static void assertDataSourceName(BasicDataSource dataSource, String dataSourceName)
      throws Exception {
    try {
      dataSource.getConnection().close();

      assertDataSourceMetrics(dataSourceName);
    } finally {
      dataSource.close();
    }

    assertNoMetrics();
  }

  private static void assertDataSourceMetrics(String dataSourceName) {
    DbConnectionPoolMetricsAssertions.create(testing, INSTRUMENTATION_NAME, dataSourceName)
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .disablePendingRequests()
        .assertConnectionPoolEmitsMetrics();
  }

  private static void assertNoMetrics() {
    testing.clearData();

    await()
        .untilAsserted(
            () ->
                assertThat(testing.metrics())
                    .filteredOn(
                        metricData ->
                            metricData
                                .getInstrumentationScopeInfo()
                                .getName()
                                .equals(INSTRUMENTATION_NAME))
                    .isEmpty());
  }
}
