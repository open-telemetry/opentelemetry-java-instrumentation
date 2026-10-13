/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.annotation.Nullable;

class CouchbaseQueryText {

  @Nullable private static final Class<?> QUERY_CLASS;
  @Nullable private static final Class<?> STATEMENT_CLASS;
  @Nullable private static final Class<?> N1QL_QUERY_CLASS;
  @Nullable private static final MethodHandle N1QL_GET_STATEMENT;
  @Nullable private static final Class<?> ANALYTICS_QUERY_CLASS;
  @Nullable private static final MethodHandle ANALYTICS_GET_STATEMENT;

  static {
    Class<?> queryClass;
    try {
      queryClass = Class.forName("com.couchbase.client.java.query.Query");
    } catch (Exception ignored) {
      queryClass = null;
    }
    QUERY_CLASS = queryClass;

    Class<?> statementClass;
    try {
      statementClass = Class.forName("com.couchbase.client.java.query.Statement");
    } catch (Exception ignored) {
      statementClass = null;
    }
    STATEMENT_CLASS = statementClass;

    Class<?> n1qlQueryClass;
    MethodHandle n1qlGetStatement;
    try {
      n1qlQueryClass = Class.forName("com.couchbase.client.java.query.N1qlQuery");
      n1qlGetStatement =
          MethodHandles.publicLookup()
              .findVirtual(
                  n1qlQueryClass,
                  "statement",
                  MethodType.methodType(
                      Class.forName("com.couchbase.client.java.query.Statement")));
    } catch (Exception ignored) {
      n1qlQueryClass = null;
      n1qlGetStatement = null;
    }
    N1QL_QUERY_CLASS = n1qlQueryClass;
    N1QL_GET_STATEMENT = n1qlGetStatement;

    Class<?> analyticsQueryClass;
    MethodHandle analyticsGetStatement;
    try {
      analyticsQueryClass = Class.forName("com.couchbase.client.java.analytics.AnalyticsQuery");
      analyticsGetStatement =
          MethodHandles.publicLookup()
              .findVirtual(analyticsQueryClass, "statement", MethodType.methodType(String.class));
    } catch (Exception ignored) {
      analyticsQueryClass = null;
      analyticsGetStatement = null;
    }
    ANALYTICS_QUERY_CLASS = analyticsQueryClass;
    ANALYTICS_GET_STATEMENT = analyticsGetStatement;
  }

  @Nullable
  static String getSqlQueryText(Object query) {
    if (query instanceof String) {
      return (String) query;
    }
    // Query is present in Couchbase [2.0.0, 2.2.0)
    // Statement is present starting from Couchbase 2.1.0
    if ((QUERY_CLASS != null && QUERY_CLASS.isAssignableFrom(query.getClass()))
        || (STATEMENT_CLASS != null && STATEMENT_CLASS.isAssignableFrom(query.getClass()))) {
      return query.toString();
    }
    // N1qlQuery is present starting from Couchbase 2.2.0
    if (N1QL_QUERY_CLASS != null && N1QL_QUERY_CLASS.isAssignableFrom(query.getClass())) {
      return getQueryText(N1QL_GET_STATEMENT, query);
    }
    // AnalyticsQuery is present starting from Couchbase 2.4.3
    if (ANALYTICS_QUERY_CLASS != null && ANALYTICS_QUERY_CLASS.isAssignableFrom(query.getClass())) {
      return getQueryText(ANALYTICS_GET_STATEMENT, query);
    }
    return null;
  }

  static String getNonSqlQueryText(Object query) {
    // SpatialViewQuery is present starting from Couchbase 2.1.0
    String queryClassName = query.getClass().getName();
    if (queryClassName.equals("com.couchbase.client.java.view.ViewQuery")
        || queryClassName.equals("com.couchbase.client.java.view.SpatialViewQuery")) {
      return query.toString();
    }
    return query.getClass().getSimpleName();
  }

  @Nullable
  private static String getQueryText(@Nullable MethodHandle handle, Object query) {
    if (handle == null) {
      return null;
    }
    try {
      return handle.invoke(query).toString();
    } catch (Throwable ignored) {
      return null;
    }
  }

  private CouchbaseQueryText() {}
}
