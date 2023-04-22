/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class VertxSqlClientInstrumentationModule extends InstrumentationModule {

  public VertxSqlClientInstrumentationModule() {
    super("vertx-sql-client", "vertx-sql-client-4.0", "vertx");
  }

  @Override
  public boolean isHelperClass(String className) {
    return "io.vertx.sqlclient.impl.QueryExecutorUtil".equals(className);
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new PoolInstrumentation(),
        new SqlClientBaseInstrumentation(),
        new QueryExecutorInstrumentation(),
        new QueryResultBuilderInstrumentation());
  }
}
