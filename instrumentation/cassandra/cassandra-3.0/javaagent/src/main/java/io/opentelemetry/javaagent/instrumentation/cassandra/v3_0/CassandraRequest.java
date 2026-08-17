/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static java.util.Collections.singleton;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

@AutoValue
abstract class CassandraRequest {

  static CassandraRequest create(Session session, String queryText, boolean parameterizedQuery) {
    return create(session, singleton(queryText), parameterizedQuery, null, null);
  }

  static CassandraRequest create(Session session, String queryText) {
    return create(session, singleton(queryText), false, null, null);
  }

  static CassandraRequest create(Session session, Statement statement) {
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
    for (Statement batchEntry : batchStatement.getStatements()) {
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

  private static String getQuery(Statement statement) {
    String query = null;
    if (statement instanceof BoundStatement) {
      query = ((BoundStatement) statement).preparedStatement().getQueryString();
    } else if (statement instanceof RegularStatement) {
      query = ((RegularStatement) statement).getQueryString();
    }

    return query == null ? "" : query;
  }

  private static boolean hasQueryValues(Statement statement) {
    if (statement instanceof BoundStatement) {
      return true;
    }
    if (statement instanceof RegularStatement) {
      return ((RegularStatement) statement).hasValues();
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
