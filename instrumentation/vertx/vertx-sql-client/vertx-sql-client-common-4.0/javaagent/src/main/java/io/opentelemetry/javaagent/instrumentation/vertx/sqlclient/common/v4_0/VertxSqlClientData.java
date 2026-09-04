/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.OTHER_SQL;

import io.vertx.sqlclient.SqlConnectOptions;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

public class VertxSqlClientData {
  @Nullable private final SqlConnectOptions connectOptions;
  @Nullable private String dbSystem;
  @Nullable private final String user;
  @Nullable private final String database;
  @Nullable private final String host;
  @Nullable private final Integer port;
  @Nullable private final VertxServerTarget serverTarget;

  public VertxSqlClientData(@Nullable SqlConnectOptions connectOptions, @Nullable String dbSystem) {
    this(
        connectOptions,
        dbSystem,
        connectOptions != null ? connectOptions.getUser() : null,
        connectOptions != null ? connectOptions.getDatabase() : null,
        connectOptions != null ? connectOptions.getHost() : null,
        connectOptions != null ? connectOptions.getPort() : null,
        null);
  }

  private VertxSqlClientData(
      SqlConnectOptions connectOptions,
      @Nullable String dbSystem,
      @Nullable String user,
      @Nullable String database,
      @Nullable String host,
      @Nullable Integer port,
      @Nullable VertxServerTarget serverTarget) {
    this.connectOptions = connectOptions;
    this.dbSystem = dbSystem;
    this.user = user;
    this.database = database;
    this.host = host;
    this.port = port;
    this.serverTarget = serverTarget;
  }

  @Nullable
  public static VertxSqlClientData create(@Nullable SqlConnectOptions connectOptions) {
    if (connectOptions == null) {
      return null;
    }
    return new VertxSqlClientData(
        connectOptions,
        null,
        connectOptions.getUser(),
        connectOptions.getDatabase(),
        connectOptions.getHost(),
        connectOptions.getPort(),
        VertxServerTarget.create(connectOptions));
  }

  @Nullable
  public static VertxSqlClientData create(@Nullable List<? extends SqlConnectOptions> databases) {
    if (databases == null || databases.isEmpty() || databases.get(0) == null) {
      return null;
    }
    SqlConnectOptions connectOptions = databases.get(0);
    return new VertxSqlClientData(
        connectOptions,
        null,
        commonUser(databases),
        commonDatabase(databases),
        connectOptions.getHost(),
        connectOptions.getPort(),
        VertxServerTarget.create(databases));
  }

  public void resolveDbSystem(String dbSystem) {
    this.dbSystem = dbSystem;
    if (serverTarget != null) {
      serverTarget.resolveDbSystem(dbSystem);
    }
  }

  @Nullable
  public SqlConnectOptions getConnectOptions() {
    return connectOptions;
  }

  @Nullable
  public String getDbSystem() {
    return dbSystem;
  }

  public String getDbSystemName() {
    return dbSystem != null ? dbSystem : OTHER_SQL;
  }

  @Nullable
  public String getUser() {
    return user;
  }

  @Nullable
  public String getDatabase() {
    return database;
  }

  @Nullable
  public String getHost() {
    return host;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }

  public boolean hasConfiguredServerTarget() {
    return serverTarget != null;
  }

  @Nullable
  public String getConfiguredServerAddress() {
    return serverTarget != null ? serverTarget.getAddress() : null;
  }

  @Nullable
  public Integer getConfiguredServerPort() {
    return serverTarget != null ? serverTarget.getPort() : null;
  }

  @Nullable
  private static String commonUser(List<? extends SqlConnectOptions> databases) {
    return commonValue(databases, true);
  }

  @Nullable
  private static String commonDatabase(List<? extends SqlConnectOptions> databases) {
    return commonValue(databases, false);
  }

  @Nullable
  private static String commonValue(
      List<? extends SqlConnectOptions> databases, boolean userValue) {
    SqlConnectOptions first = databases.get(0);
    String value = userValue ? first.getUser() : first.getDatabase();
    for (int i = 1; i < databases.size(); i++) {
      SqlConnectOptions database = databases.get(i);
      if (database == null
          || !Objects.equals(value, userValue ? database.getUser() : database.getDatabase())) {
        return null;
      }
    }
    return value;
  }
}
