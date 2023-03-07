/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.R2dbcSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.listener.ProxyMethodExecutionListener;
import io.r2dbc.spi.ConnectionFactoryOptions;

public class TraceProxyListener implements ProxyMethodExecutionListener {

  private static final String KEY_DB_EXECUTION = "dbExecution";

  private final ConnectionFactoryOptions factoryOptions;

  public TraceProxyListener(ConnectionFactoryOptions factoryOptions) {
    this.factoryOptions = factoryOptions;
  }

  @Override
  public void beforeQuery(QueryExecutionInfo queryInfo) {
    Context parentContext = currentContext();
    DbExecution dbExecution = new DbExecution(queryInfo, factoryOptions);
    if (!instrumenter().shouldStart(parentContext, dbExecution)) {
      return;
    }
    dbExecution.setContext(instrumenter().start(parentContext, dbExecution));
    queryInfo.getValueStore().put(KEY_DB_EXECUTION, dbExecution);
  }

  @Override
  public void afterQuery(QueryExecutionInfo queryInfo) {
    DbExecution dbExecution = (DbExecution) queryInfo.getValueStore().get(KEY_DB_EXECUTION);
    if (dbExecution != null && dbExecution.getContext() != null) {
      instrumenter().end(dbExecution.getContext(), dbExecution, null, null);
    }
  }
}
