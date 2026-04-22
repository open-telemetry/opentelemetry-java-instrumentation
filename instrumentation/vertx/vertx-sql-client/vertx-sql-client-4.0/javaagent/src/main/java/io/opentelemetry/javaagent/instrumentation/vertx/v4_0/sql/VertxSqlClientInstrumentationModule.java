/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class VertxSqlClientInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public VertxSqlClientInstrumentationModule() {
    super("vertx-sql-client", "vertx-sql-client-4.0", "vertx");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // removed in 5.0
    return hasClassesNamed("io.vertx.sqlclient.impl.SqlClientBase");
  }

  @Override
  public boolean isHelperClass(String className) {
    return "io.vertx.sqlclient.impl.QueryExecutorUtil".equals(className);
  }

  @Override
  public List<String> injectedClassNames() {
    return singletonList("io.vertx.sqlclient.impl.QueryExecutorUtil");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new PoolInstrumentation(),
        new SqlClientBaseInstrumentation(),
        new QueryExecutorInstrumentation(),
        new QueryResultBuilderInstrumentation(),
        new TransactionImplInstrumentation());
  }
}
