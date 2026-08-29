/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static java.util.Collections.singleton;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.QueryOptions;
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
    return create(session, queryText, parameterizedQuery, null);
  }

  static CassandraRequest create(
      Session session,
      String queryText,
      boolean parameterizedQuery,
      @Nullable CassandraConfiguredTarget configuredTarget) {
    return create(
        session, singleton(queryText), parameterizedQuery, null, null, null, configuredTarget);
  }

  static CassandraRequest create(Session session, String queryText) {
    return create(session, queryText, null);
  }

  static CassandraRequest create(
      Session session, String queryText, @Nullable CassandraConfiguredTarget configuredTarget) {
    return create(session, singleton(queryText), false, null, null, null, configuredTarget);
  }

  static CassandraRequest create(Session session, Statement statement) {
    return create(session, statement, null);
  }

  static CassandraRequest create(
      Session session, Statement statement, @Nullable CassandraConfiguredTarget configuredTarget) {
    if (statement instanceof BatchStatement) {
      return create(session, (BatchStatement) statement, configuredTarget);
    }
    return create(
        session,
        singleton(getQuery(statement)),
        hasQueryValues(statement),
        null,
        null,
        statement,
        configuredTarget);
  }

  private static CassandraRequest create(
      Session session,
      BatchStatement batchStatement,
      @Nullable CassandraConfiguredTarget configuredTarget) {
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
        Long.valueOf(batchStatement.size()),
        batchStatement,
        configuredTarget);
  }

  private static CassandraRequest create(
      Session session,
      Collection<String> queryTexts,
      boolean allQueriesParameterized,
      @Nullable List<Boolean> mixedParameterizedQueries,
      @Nullable Long batchSize,
      @Nullable Statement statement,
      @Nullable CassandraConfiguredTarget configuredTarget) {
    QueryOptions queryOptions = session.getCluster().getConfiguration().getQueryOptions();
    String consistencyLevel =
        (statement == null || statement.getConsistencyLevel() == null)
            ? queryOptions.getConsistencyLevel().name()
            : statement.getConsistencyLevel().name();
    int fetchSize =
        (statement == null || statement.getFetchSize() <= 0)
            ? queryOptions.getFetchSize()
            : statement.getFetchSize();
    // the driver treats Integer.MAX_VALUE as a request to disable paging and never sends a page
    // size, so there is no page size to report
    Long pageSize =
        (fetchSize <= 0 || fetchSize == Integer.MAX_VALUE) ? null : Long.valueOf(fetchSize);
    Boolean idempotent = statement == null ? null : statement.isIdempotent();
    boolean queryIdempotent =
        idempotent == null ? queryOptions.getDefaultIdempotence() : idempotent;
    return new AutoValue_CassandraRequest(
        session,
        queryTexts,
        allQueriesParameterized,
        mixedParameterizedQueries,
        batchSize,
        consistencyLevel,
        pageSize,
        queryIdempotent,
        configuredTarget);
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

  abstract String getConsistencyLevel();

  @Nullable
  abstract Long getPageSize();

  abstract boolean isIdempotent();

  @Nullable
  abstract CassandraConfiguredTarget getConfiguredTarget();
}
