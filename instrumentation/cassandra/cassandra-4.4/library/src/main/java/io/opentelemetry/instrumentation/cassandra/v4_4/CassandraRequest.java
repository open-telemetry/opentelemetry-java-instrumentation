/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.session.Session;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class CassandraRequest {

  public static CassandraRequest create(Session session, String dbQueryText) {
    return new AutoValue_CassandraRequest(session, dbQueryText);
  }

  public abstract Session getSession();

  public abstract String getDbQueryText();
}
