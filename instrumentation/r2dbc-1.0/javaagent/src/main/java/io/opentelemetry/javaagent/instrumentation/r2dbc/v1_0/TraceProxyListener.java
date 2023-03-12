/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.R2dbcSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.listener.ProxyMethodExecutionListener;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.util.HashMap;
import java.util.Map;

public class TraceProxyListener implements ProxyMethodExecutionListener {

  private final ConnectionFactoryOptions factoryOptions;
  private final Map<QueryExecutionInfo, DbExecution> dbExecutions = new HashMap<>();

  public TraceProxyListener(ConnectionFactoryOptions factoryOptions) {
    this.factoryOptions = factoryOptions;
  }

  @Override
  public void beforeQuery(QueryExecutionInfo queryInfo) {
    Context parentContext = currentContext();
    DbExecution dbExecution = new DbExecution(queryInfo, factoryOptions);
    dbExecutions.put(queryInfo, dbExecution);
    if (!instrumenter().shouldStart(parentContext, dbExecution)) {
      return;
    }
    Context context = instrumenter().start(parentContext, dbExecution);
    dbExecution.setContext(context);
    Scope scope = parentContext.makeCurrent();
    dbExecution.setScope(scope);
  }

  @Override
  public void afterQuery(QueryExecutionInfo queryInfo) {
    DbExecution dbExecution = dbExecutions.remove(queryInfo);
    if (dbExecution.getScope() == null) {
      return;
    }
    instrumenter().end(dbExecution.getContext(), dbExecution, null, null);
    dbExecution.getScope().close();
  }
}
