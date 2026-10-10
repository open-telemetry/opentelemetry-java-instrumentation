/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.OTHER_SQL;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.vertx.sqlclient.SqlConnectOptions;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

public final class VertxSqlClientInfo {

  private final String dbSystemName;
  @Nullable private final String namespace;
  @Nullable private final DbServerTarget serverTarget;

  @Nullable
  public static VertxSqlClientInfo create(
      @Nullable SqlConnectOptions connectOptions, @Nullable String dbSystemName) {
    if (connectOptions == null) {
      return null;
    }
    String normalizedDbSystemName = normalizedDbSystemName(dbSystemName);
    return new VertxSqlClientInfo(
        normalizedDbSystemName,
        connectOptions.getDatabase(),
        VertxServerTarget.from(connectOptions, normalizedDbSystemName));
  }

  @Nullable
  public static VertxSqlClientInfo create(
      @Nullable List<? extends SqlConnectOptions> connectOptions, @Nullable String dbSystemName) {
    if (connectOptions == null || connectOptions.isEmpty() || connectOptions.get(0) == null) {
      return null;
    }
    String normalizedDbSystemName = normalizedDbSystemName(dbSystemName);
    return new VertxSqlClientInfo(
        normalizedDbSystemName,
        commonNamespace(connectOptions),
        VertxServerTarget.from(connectOptions, normalizedDbSystemName));
  }

  public static VertxSqlClientInfo createUnknown(@Nullable String dbSystemName) {
    return new VertxSqlClientInfo(normalizedDbSystemName(dbSystemName), null, null);
  }

  private VertxSqlClientInfo(
      String dbSystemName, @Nullable String namespace, @Nullable DbServerTarget serverTarget) {
    this.dbSystemName = dbSystemName;
    this.namespace = namespace;
    this.serverTarget = serverTarget;
  }

  public String getDbSystemName() {
    return dbSystemName;
  }

  @Nullable
  public String getNamespace() {
    return namespace;
  }

  @Nullable
  public DbServerTarget getServerTarget() {
    return serverTarget;
  }

  @Nullable
  private static String commonNamespace(List<? extends SqlConnectOptions> connectOptions) {
    SqlConnectOptions first = connectOptions.get(0);
    String value = first.getDatabase();
    for (int i = 1; i < connectOptions.size(); i++) {
      SqlConnectOptions options = connectOptions.get(i);
      if (options == null || !Objects.equals(value, options.getDatabase())) {
        return null;
      }
    }
    return value;
  }

  private static String normalizedDbSystemName(@Nullable String dbSystemName) {
    return dbSystemName != null ? dbSystemName : OTHER_SQL;
  }
}
