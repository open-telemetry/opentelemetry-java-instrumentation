/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.listener.ProxyMethodExecutionListener;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class TraceProxyListener implements ProxyMethodExecutionListener {

  private static final String KEY_DB_EXECUTION = "dbExecution";

  private final Instrumenter<DbExecution, Void> instrumenter;
  private final ConnectionFactoryOptions factoryOptions;

  public TraceProxyListener(
      Instrumenter<DbExecution, Void> instrumenter, ConnectionFactoryOptions factoryOptions) {
    this.instrumenter = instrumenter;
    this.factoryOptions = factoryOptions;
  }

  @Override
  public void beforeQuery(QueryExecutionInfo queryInfo) {
    Context parentContext = Context.current();
    DbExecution dbExecution = new DbExecution(queryInfo, factoryOptions);
    if (!instrumenter.shouldStart(parentContext, dbExecution)) {
      return;
    }
    dbExecution.setContext(instrumenter.start(parentContext, dbExecution));
    queryInfo.getValueStore().put(KEY_DB_EXECUTION, dbExecution);
  }

  @Override
  public void afterQuery(QueryExecutionInfo queryInfo) {
    DbExecution dbExecution = (DbExecution) queryInfo.getValueStore().get(KEY_DB_EXECUTION);
    if (dbExecution != null && dbExecution.getContext() != null) {
      instrumenter.end(dbExecution.getContext(), dbExecution, null, queryInfo.getThrowable());
    }
  }
}
