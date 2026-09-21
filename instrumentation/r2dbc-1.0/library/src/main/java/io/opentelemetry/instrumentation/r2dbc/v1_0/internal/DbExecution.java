/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import static java.util.stream.Collectors.toList;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DbExecution {
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String OTHER_SQL = "other_sql";

  private final R2dbcConnectionInfo connectionInfo;
  private final String system;
  private final List<String> rawQueryTexts;
  @Nullable private final Long batchSize;
  private final boolean parameterizedQuery;

  @Nullable private Context context;

  public DbExecution(QueryExecutionInfo queryInfo, ConnectionFactoryOptions factoryOptions) {
    this(queryInfo, new R2dbcConnectionInfo(factoryOptions));
  }

  DbExecution(QueryExecutionInfo queryInfo, R2dbcConnectionInfo connectionInfo) {
    this.connectionInfo = connectionInfo;
    Connection originalConnection = queryInfo.getConnectionInfo().getOriginalConnection();
    this.system =
        originalConnection != null
            ? originalConnection
                .getMetadata()
                .getDatabaseProductName()
                .toLowerCase(Locale.ROOT)
                .split(" ")[0]
            : OTHER_SQL;
    this.rawQueryTexts =
        queryInfo.getQueries().stream()
            .map(QueryInfo::getQuery)
            .map(
                query ->
                    R2dbcSqlCommenterUtil.getOriginalQuery(queryInfo.getConnectionInfo(), query))
            .collect(toList());
    int queryInfoBatchSize = queryInfo.getBatchSize();
    // r2dbc-proxy reports 0 as the default size for ordinary non-batch executions. Those still
    // have a query text; an empty Batch.execute() is represented with no query texts.
    boolean emptyBatch = rawQueryTexts.isEmpty();
    this.batchSize = queryInfoBatchSize > 1 || emptyBatch ? (long) queryInfoBatchSize : null;
    this.parameterizedQuery =
        queryInfo.getQueries().stream()
            .anyMatch(queryInfo1 -> !queryInfo1.getBindingsList().isEmpty());
    R2dbcSqlCommenterUtil.clearQueries(queryInfo.getConnectionInfo());
  }

  R2dbcConnectionInfo connectionInfo() {
    return connectionInfo;
  }

  @Nullable
  public String getServerAddress() {
    return connectionInfo.getServerAddress();
  }

  @Nullable
  public Integer getServerPort() {
    return connectionInfo.getServerPort();
  }

  @Nullable
  public String getConfiguredServerAddress() {
    DbServerTarget target = connectionInfo.getConfiguredServerTarget();
    return target == null ? null : target.getAddress();
  }

  @Nullable
  public Integer getConfiguredServerPort() {
    DbServerTarget target = connectionInfo.getConfiguredServerTarget();
    return target == null ? null : target.getPort();
  }

  public String getSystemName() {
    return connectionInfo.getSystemName();
  }

  @Deprecated // to be removed in 3.0
  public String getSystem() {
    return system;
  }

  @Nullable
  public String getUser() {
    return connectionInfo.getUser();
  }

  @Nullable
  public String getNamespace() {
    return connectionInfo.getNamespace();
  }

  public String getConnectionString() {
    return connectionInfo.getConnectionString();
  }

  public List<String> getRawQueryTexts() {
    return rawQueryTexts;
  }

  @Nullable
  public Long getBatchSize() {
    return batchSize;
  }

  public boolean isParameterizedQuery() {
    return parameterizedQuery;
  }

  @Nullable
  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }
}
