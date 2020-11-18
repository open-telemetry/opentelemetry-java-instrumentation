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
      packageName + ".DbInfo",
      packageName + ".DbInfo$Builder",
      packageName + ".JdbcConnectionUrlParser",
      packageName + ".JdbcConnectionUrlParser$1",
      packageName + ".JdbcConnectionUrlParser$2",
      packageName + ".JdbcConnectionUrlParser$3",
      packageName + ".JdbcConnectionUrlParser$4",
      packageName + ".JdbcConnectionUrlParser$5",
      packageName + ".JdbcConnectionUrlParser$6",
      packageName + ".JdbcConnectionUrlParser$7",
      packageName + ".JdbcConnectionUrlParser$8",
      packageName + ".JdbcConnectionUrlParser$9",
      packageName + ".JdbcConnectionUrlParser$10",
      packageName + ".JdbcConnectionUrlParser$11",
      packageName + ".JdbcConnectionUrlParser$12",
      packageName + ".JdbcConnectionUrlParser$13",
      packageName + ".JdbcConnectionUrlParser$14",
      packageName + ".JdbcConnectionUrlParser$15",
      packageName + ".JdbcConnectionUrlParser$16",
      packageName + ".JdbcConnectionUrlParser$17",
      packageName + ".JdbcMaps",
      packageName + ".JdbcTracer",
      packageName + ".JdbcUtils",
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
