/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;

/** Holds span context and row count for a ResultSet whose span end is deferred to close(). */
public final class ResultSetInfo {

  private final Context context;
  private final DbRequest request;
  private long rowCount;

  ResultSetInfo(Context context, DbRequest request) {
    this.context = context;
    this.request = request;
    this.rowCount = 0;
  }

  Context getContext() {
    return context;
  }

  DbRequest getRequest() {
    return request;
  }

  void incrementRowCount() {
    rowCount++;
  }

  long getRowCount() {
    return rowCount;
  }
}
