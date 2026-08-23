/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.alibabadruid.v1_0;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.stat.DruidDataSourceStatManager;
import io.opentelemetry.instrumentation.alibabadruid.AbstractDruidInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DruidInstrumentationTest extends AbstractDruidInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(DruidDataSource dataSource, String name) throws Exception {
    DruidDataSourceStatManager.addDataSource(dataSource, name);
  }

  @Override
  protected void shutdown(DruidDataSource dataSource) throws Exception {
    DruidDataSourceStatManager.removeDataSource(dataSource);
  }

  @Test
  void shouldUseJdbcUrlForDataSourceNameWhenNameIsNull() throws Exception {
    DruidDataSource dataSource = createDataSource();
    dataSource.setUrl("jdbc:postgresql://db.example:5432/orders");

    assertDataSourceName(dataSource, "db.example:5432/orders");
  }

  @Test
  void shouldUseIpv6JdbcUrlForDataSourceNameWhenNameIsNull() throws Exception {
    DruidDataSource dataSource = createDataSource();
    dataSource.setUrl("jdbc:postgresql://[2001:db8::1]:5432/orders");

    assertDataSourceName(dataSource, "[2001:db8::1]:5432/orders");
  }

  @Test
  void shouldUseConnectPropertiesForDataSourceNameWhenNameIsNull() throws Exception {
    DruidDataSource dataSource = createDataSource();
    dataSource.setUrl("jdbc:postgresql:ignored");
    dataSource.addConnectionProperty("serverName", "properties.example");
    dataSource.addConnectionProperty("portNumber", "5433");
    dataSource.addConnectionProperty("databaseName", "inventory");

    assertDataSourceName(dataSource, "properties.example:5433/inventory");
  }

  @Test
  void shouldUseServerAddressWhenPortAndNamespaceAreMissing() throws Exception {
    DruidDataSource dataSource = createDataSource();
    dataSource.setUrl("jdbc:custom:ignored");
    dataSource.addConnectionProperty("serverName", "address-only.example");

    assertDataSourceName(dataSource, "address-only.example");
  }

  @Test
  void shouldUseDbNamespaceWhenServerAddressIsMissing() throws Exception {
    DruidDataSource dataSource = createDataSource();
    dataSource.setUrl("jdbc:h2:mem:orders");

    assertDataSourceName(dataSource, "orders");
  }

  @Test
  void shouldUseFixedDataSourceNameWhenConnectionInfoIsMissing() throws Exception {
    DruidDataSource dataSource = createDataSource();

    assertDataSourceName(dataSource, "alibaba-druid");
  }

  private void assertDataSourceName(DruidDataSource dataSource, String dataSourceName)
      throws Exception {
    try {
      configure(dataSource, null);

      assertConnectionUsagePoolNames(dataSourceName);
    } finally {
      dataSource.close();
      shutdown(dataSource);
    }

    assertNoMetrics();
  }
}
