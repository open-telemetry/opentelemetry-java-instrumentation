/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static java.util.Collections.singleton;

import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchableStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.session.Session;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

@AutoValue
abstract class CassandraRequest {

  static CassandraRequest create(Session session, String queryText) {
    return create(session, singleton(queryText), false, null, null);
  }

  static CassandraRequest create(Session session, Statement<?> statement) {
    if (statement instanceof BatchStatement) {
      return create(session, (BatchStatement) statement);
    }
    return create(
        session, singleton(getQuery(statement)), statement instanceof BoundStatement, null, null);
  }

  private static CassandraRequest create(Session session, BatchStatement batchStatement) {
    List<String> queryTexts = new ArrayList<>();
    List<Boolean> parameterizedQueries = null;
    boolean allParameterized = true;
    Boolean firstParameterizedQuery = null;
    int queryIndex = 0;
    for (BatchableStatement<?> batchEntry : batchStatement) {
      queryTexts.add(getQuery(batchEntry));
      boolean parameterizedQuery = batchEntry instanceof BoundStatement;
      if (!parameterizedQuery) {
        allParameterized = false;
      }
      if (firstParameterizedQuery == null) {
        firstParameterizedQuery = parameterizedQuery;
      } else if (parameterizedQuery != firstParameterizedQuery && parameterizedQueries == null) {
        parameterizedQueries = new ArrayList<>(batchStatement.size());
        for (int previousQueryIndex = 0; previousQueryIndex < queryIndex; previousQueryIndex++) {
          parameterizedQueries.add(firstParameterizedQuery);
        }
      }
      if (parameterizedQueries != null) {
        parameterizedQueries.add(parameterizedQuery);
      }
      queryIndex++;
    }
    boolean parameterizedQuery = allParameterized;
    if (parameterizedQueries == null && firstParameterizedQuery != null) {
      parameterizedQuery = firstParameterizedQuery;
    }
    return create(
        session,
        queryTexts,
        parameterizedQuery,
        parameterizedQueries,
        Long.valueOf(batchStatement.size()));
  }

  private static CassandraRequest create(
      Session session,
      Collection<String> queryTexts,
      boolean parameterizedQuery,
      @Nullable List<Boolean> parameterizedQueries,
      @Nullable Long batchSize) {
    return new AutoValue_CassandraRequest(
        session, queryTexts, parameterizedQuery, parameterizedQueries, batchSize);
  }

  private static String getQuery(Statement<?> statement) {
    String query = null;
    if (statement instanceof SimpleStatement) {
      query = ((SimpleStatement) statement).getQuery();
    } else if (statement instanceof BoundStatement) {
      query = ((BoundStatement) statement).getPreparedStatement().getQuery();
    }

    return query == null ? "" : query;
  }

  abstract Session getSession();

  abstract Collection<String> getQueryTexts();

  abstract boolean parameterizedQuery();

  @Nullable
  abstract List<Boolean> getParameterizedQueries();

  boolean isParameterizedQuery(int queryIndex) {
    List<Boolean> parameterizedQueries = getParameterizedQueries();
    return parameterizedQueries == null
        ? parameterizedQuery()
        : parameterizedQueries.get(queryIndex);
  }

  @Nullable
  abstract Long getBatchSize();
}
