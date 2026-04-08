/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.session.Session;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class CassandraRequest {

  public static CassandraRequest create(
      Session session, String queryText, boolean parameterizedQuery) {
    return new AutoValue_CassandraRequest(session, queryText, parameterizedQuery);
  }

  public abstract Session getSession();

  public abstract String getQueryText();

  public abstract boolean isParameterizedQuery();
}
