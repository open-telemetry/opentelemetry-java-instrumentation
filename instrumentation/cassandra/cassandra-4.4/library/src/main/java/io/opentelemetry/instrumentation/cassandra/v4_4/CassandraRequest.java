/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

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
public abstract class CassandraRequest {

  static CassandraRequest create(Session session, String queryText) {
    return create(session, singleton(queryText), false, null, null);
  }

  static CassandraRequest create(Session session, Statement<?> statement) {
    if (statement instanceof BatchStatement) {
      return create(session, (BatchStatement) statement);
    }
    return create(session, singleton(getQuery(statement)), hasQueryValues(statement), null, null);
  }

  private static CassandraRequest create(Session session, BatchStatement batchStatement) {
    List<String> queryTexts = new ArrayList<>();
    List<Boolean> mixedParameterizedQueries = null;
    boolean allQueriesParameterized = true;
    Boolean firstParameterizedQuery = null;
    int queryIndex = 0;
    for (BatchableStatement<?> batchEntry : batchStatement) {
      queryTexts.add(getQuery(batchEntry));
      boolean parameterizedQuery = hasQueryValues(batchEntry);
      if (!parameterizedQuery) {
        allQueriesParameterized = false;
      }
      if (firstParameterizedQuery == null) {
        firstParameterizedQuery = parameterizedQuery;
      } else if (parameterizedQuery != firstParameterizedQuery
          && mixedParameterizedQueries == null) {
        mixedParameterizedQueries = new ArrayList<>(batchStatement.size());
        for (int previousQueryIndex = 0; previousQueryIndex < queryIndex; previousQueryIndex++) {
          mixedParameterizedQueries.add(firstParameterizedQuery);
        }
      }
      if (mixedParameterizedQueries != null) {
        mixedParameterizedQueries.add(parameterizedQuery);
      }
      queryIndex++;
    }
    boolean allQueriesParameterizedResult = allQueriesParameterized;
    if (mixedParameterizedQueries == null && firstParameterizedQuery != null) {
      allQueriesParameterizedResult = firstParameterizedQuery;
    }
    return create(
        session,
        queryTexts,
        allQueriesParameterizedResult,
        mixedParameterizedQueries,
        Long.valueOf(batchStatement.size()));
  }

  private static CassandraRequest create(
      Session session,
      Collection<String> queryTexts,
      boolean allQueriesParameterized,
      @Nullable List<Boolean> mixedParameterizedQueries,
      @Nullable Long batchSize) {
    return new AutoValue_CassandraRequest(
        session, queryTexts, allQueriesParameterized, mixedParameterizedQueries, batchSize);
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

  private static boolean hasQueryValues(Statement<?> statement) {
    if (statement instanceof BoundStatement) {
      return true;
    }
    if (statement instanceof SimpleStatement) {
      SimpleStatement simpleStatement = (SimpleStatement) statement;
      return !simpleStatement.getPositionalValues().isEmpty()
          || !simpleStatement.getNamedValues().isEmpty();
    }
    return false;
  }

  abstract Session getSession();

  abstract Collection<String> getQueryTexts();

  abstract boolean allQueriesParameterized();

  @Nullable
  abstract List<Boolean> mixedParameterizedQueries();

  boolean isParameterizedQuery(int queryIndex) {
    List<Boolean> mixedParameterizedQueries = mixedParameterizedQueries();
    return mixedParameterizedQueries == null
        ? allQueriesParameterized()
        : mixedParameterizedQueries.get(queryIndex);
  }

  @Nullable
  abstract Long getBatchSize();
}
