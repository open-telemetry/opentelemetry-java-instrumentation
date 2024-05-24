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
}
