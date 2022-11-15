/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_14;

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet;
import com.datastax.dse.driver.internal.core.cql.reactive.DefaultReactiveResultSet;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.Statement;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.cassandra.v4_0.CassandraRequest;

public class TracingCqlSession
    extends io.opentelemetry.javaagent.instrumentation.cassandra.v4_0.TracingCqlSession {

  public TracingCqlSession(
      CqlSession session, Instrumenter<CassandraRequest, ExecutionInfo> instrumenter) {
    super(session, instrumenter);
  }

  @Override
  public ReactiveResultSet executeReactive(Statement<?> statement) {
    return new DefaultReactiveResultSet(() -> executeAsync(statement));
  }
}
