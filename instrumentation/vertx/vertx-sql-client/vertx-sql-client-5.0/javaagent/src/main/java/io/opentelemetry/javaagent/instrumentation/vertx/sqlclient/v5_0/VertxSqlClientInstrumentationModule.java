/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import java.util.function.BiConsumer;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class VertxSqlClientInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public VertxSqlClientInstrumentationModule() {
    super("vertx-sql-client", "vertx-sql-client-5.0", "vertx");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 5.0
    return hasClassesNamed("io.vertx.sqlclient.internal.SqlClientBase");
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
        new CommandSchedulerInstrumentation(),
        new DriverInstrumentation(),
        new PoolInstrumentation(),
        new SqlClientBaseInstrumentation(),
        new QueryExecutorInstrumentation(),
        new QueryResultBuilderInstrumentation(),
        new TransactionImplInstrumentation());
  }

  @Override
  public void registerVirtualFields(BiConsumer<String, String> virtualFieldRegistrar) {
    // we add the virtual field to CommandBase manually because it is in different package in 5.0
    // and 5.1
    // used in 5.0
    virtualFieldRegistrar.accept(
        "io.vertx.sqlclient.internal.command.CommandBase", Context.class.getName());
    // used in 5.1
    virtualFieldRegistrar.accept(
        "io.vertx.sqlclient.spi.protocol.CommandBase", Context.class.getName());
  }
}
