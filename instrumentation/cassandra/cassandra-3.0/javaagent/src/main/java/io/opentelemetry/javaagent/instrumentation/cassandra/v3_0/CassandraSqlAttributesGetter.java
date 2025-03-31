/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static java.util.Collections.singleton;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.util.Collection;
import javax.annotation.Nullable;

final class CassandraSqlAttributesGetter implements SqlClientAttributesGetter<CassandraRequest, Void> {

  @SuppressWarnings("deprecation") // using deprecated DbSystemIncubatingValues
  @Override
  public String getDbSystem(CassandraRequest request) {
    return DbIncubatingAttributes.DbSystemIncubatingValues.CASSANDRA;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(CassandraRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbNamespace(CassandraRequest request) {
    return request.getSession().getLoggedKeyspace();
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(CassandraRequest request) {
    return null;
  }

  @Override
  public Collection<String> getRawQueryTexts(CassandraRequest request) {
    return singleton(request.getQueryText());
  }
}
