/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.Session;

public final class CassandraRequest {

  private final Session session;
  private final String statement;

  public CassandraRequest(Session session, String statement) {
    this.session = session;
    this.statement = statement;
  }

  public Session getSession() {
    return session;
  }

  public String getStatement() {
    return statement;
  }
}
