/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.alibabadruid.v1_0;

import com.alibaba.druid.pool.DruidDataSource;
import io.opentelemetry.instrumentation.alibabadruid.AbstractDruidInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import javax.management.ObjectName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class DruidInstrumentationTest extends AbstractDruidInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static DruidTelemetry telemetry;

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @BeforeAll
  static void setup() {
    telemetry = DruidTelemetry.create(testing.getOpenTelemetry());
  }

  @Override
  protected void configure(DruidDataSource dataSource, String name) throws Exception {
    ObjectName objectName = new ObjectName("com.alibaba.druid:type=DruidDataSource,id=" + name);
    telemetry.registerMetrics(
        dataSource, objectName.getKeyProperty("type") + "-" + objectName.getKeyProperty("id"));
  }

  @Override
  protected void shutdown(DruidDataSource dataSource) throws Exception {
    telemetry.unregisterMetrics(dataSource);
  }
}
