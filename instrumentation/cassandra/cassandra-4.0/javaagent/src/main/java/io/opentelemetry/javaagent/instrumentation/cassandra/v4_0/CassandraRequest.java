/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.session.Session;
import com.google.auto.value.AutoValue;

@AutoValue
abstract class CassandraRequest {

  static CassandraRequest create(Session session, String queryText, boolean parameterizedQuery) {
    return new AutoValue_CassandraRequest(session, queryText, parameterizedQuery);
  }

  abstract Session getSession();

  abstract String getQueryText();

  abstract boolean isParameterizedQuery();
}
