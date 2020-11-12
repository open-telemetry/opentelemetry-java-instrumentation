/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JdbcInstrumentationModule extends InstrumentationModule {
  public JdbcInstrumentationModule() {
    super("jdbc");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".DBInfo",
      packageName + ".DBInfo$Builder",
      packageName + ".JDBCConnectionUrlParser",
      packageName + ".JDBCConnectionUrlParser$1",
      packageName + ".JDBCConnectionUrlParser$2",
      packageName + ".JDBCConnectionUrlParser$3",
      packageName + ".JDBCConnectionUrlParser$4",
      packageName + ".JDBCConnectionUrlParser$5",
      packageName + ".JDBCConnectionUrlParser$6",
      packageName + ".JDBCConnectionUrlParser$7",
      packageName + ".JDBCConnectionUrlParser$8",
      packageName + ".JDBCConnectionUrlParser$9",
      packageName + ".JDBCConnectionUrlParser$10",
      packageName + ".JDBCConnectionUrlParser$11",
      packageName + ".JDBCConnectionUrlParser$12",
      packageName + ".JDBCConnectionUrlParser$13",
      packageName + ".JDBCConnectionUrlParser$14",
      packageName + ".JDBCConnectionUrlParser$15",
      packageName + ".JDBCConnectionUrlParser$16",
      packageName + ".JDBCConnectionUrlParser$17",
      packageName + ".JDBCMaps",
      packageName + ".JdbcTracer",
      packageName + ".JDBCUtils",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ConnectionInstrumentation(),
        new DriverInstrumentation(),
        new PreparedStatementInstrumentation(),
        new StatementInstrumentation());
  }
}
