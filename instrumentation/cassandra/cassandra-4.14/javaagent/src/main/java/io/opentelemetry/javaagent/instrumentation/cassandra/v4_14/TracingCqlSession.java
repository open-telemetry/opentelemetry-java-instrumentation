/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_14;

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet;
import com.datastax.dse.driver.internal.core.cql.reactive.DefaultReactiveResultSet;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Statement;

public class TracingCqlSession
    extends io.opentelemetry.instrumentation.cassandra.v4_0.TracingCqlSession {

  public TracingCqlSession(CqlSession session) {
    super(session);
  }

  @Override
  public ReactiveResultSet executeReactive(Statement<?> statement) {
    return new DefaultReactiveResultSet(() -> executeAsync(statement));
  }
}
