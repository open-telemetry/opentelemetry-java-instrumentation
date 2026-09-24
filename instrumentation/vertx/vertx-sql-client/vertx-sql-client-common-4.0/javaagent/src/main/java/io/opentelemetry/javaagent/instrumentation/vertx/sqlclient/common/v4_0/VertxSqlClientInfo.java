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
  @Nullable private final String user;
  @Nullable private final String legacyServerAddress;
  @Nullable private final Integer legacyServerPort;
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
        connectOptions.getUser(),
        connectOptions.getHost(),
        connectOptions.getPort(),
        VertxServerTarget.from(connectOptions, normalizedDbSystemName));
  }

  @Nullable
  public static VertxSqlClientInfo create(
      @Nullable List<? extends SqlConnectOptions> connectOptions, @Nullable String dbSystemName) {
    if (connectOptions == null || connectOptions.isEmpty() || connectOptions.get(0) == null) {
      return null;
    }
    SqlConnectOptions first = connectOptions.get(0);
    String normalizedDbSystemName = normalizedDbSystemName(dbSystemName);
    return new VertxSqlClientInfo(
        normalizedDbSystemName,
        commonValue(connectOptions, false),
        commonValue(connectOptions, true),
        first.getHost(),
        first.getPort(),
        VertxServerTarget.from(connectOptions, normalizedDbSystemName));
  }

  public static VertxSqlClientInfo createUnknown(@Nullable String dbSystemName) {
    return new VertxSqlClientInfo(
        normalizedDbSystemName(dbSystemName), null, null, null, null, null);
  }

  private VertxSqlClientInfo(
      String dbSystemName,
      @Nullable String namespace,
      @Nullable String user,
      @Nullable String legacyServerAddress,
      @Nullable Integer legacyServerPort,
      @Nullable DbServerTarget serverTarget) {
    this.dbSystemName = dbSystemName;
    this.namespace = namespace;
    this.user = user;
    this.legacyServerAddress = legacyServerAddress;
    this.legacyServerPort = legacyServerPort;
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
  public String getUser() {
    return user;
  }

  @Nullable
  public String getLegacyServerAddress() {
    return legacyServerAddress;
  }

  @Nullable
  public Integer getLegacyServerPort() {
    return legacyServerPort;
  }

  @Nullable
  public DbServerTarget getServerTarget() {
    return serverTarget;
  }

  @Nullable
  private static String commonValue(
      List<? extends SqlConnectOptions> connectOptions, boolean userValue) {
    SqlConnectOptions first = connectOptions.get(0);
    String value = userValue ? first.getUser() : first.getDatabase();
    for (int i = 1; i < connectOptions.size(); i++) {
      SqlConnectOptions options = connectOptions.get(i);
      if (options == null
          || !Objects.equals(value, userValue ? options.getUser() : options.getDatabase())) {
        return null;
      }
    }
    return value;
  }

  private static String normalizedDbSystemName(@Nullable String dbSystemName) {
    return dbSystemName != null ? dbSystemName : OTHER_SQL;
  }
}
