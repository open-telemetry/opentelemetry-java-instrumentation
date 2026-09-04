/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import io.vertx.sqlclient.SqlConnectOptions;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class VertxSqlAddressGroup {

  private final List<Endpoint> endpoints;
  @Nullable private final String address;
  @Nullable private final Integer port;

  @Nullable
  public static VertxSqlAddressGroup of(@Nullable SqlConnectOptions database) {
    return of(database, null);
  }

  @Nullable
  public static VertxSqlAddressGroup of(
      @Nullable SqlConnectOptions database, @Nullable String dbSystem) {
    Endpoint endpoint = Endpoint.from(database);
    if (endpoint == null) {
      return null;
    }
    List<Endpoint> endpoints = new ArrayList<>(1);
    endpoints.add(endpoint);
    return new VertxSqlAddressGroup(endpoints, dbSystem);
  }

  @Nullable
  public static VertxSqlAddressGroup of(@Nullable List<? extends SqlConnectOptions> databases) {
    return of(databases, null);
  }

  @Nullable
  public static VertxSqlAddressGroup of(
      @Nullable List<? extends SqlConnectOptions> databases, @Nullable String dbSystem) {
    if (databases == null || databases.isEmpty()) {
      return null;
    }
    List<Endpoint> endpoints = new ArrayList<>(databases.size());
    for (SqlConnectOptions database : databases) {
      Endpoint endpoint = Endpoint.from(database);
      if (endpoint == null) {
        return null;
      }
      endpoints.add(endpoint);
    }
    return new VertxSqlAddressGroup(endpoints, dbSystem);
  }

  private VertxSqlAddressGroup(List<Endpoint> endpoints, @Nullable String dbSystem) {
    this.endpoints = endpoints;
    DbServerTarget target = buildTarget(endpoints, dbSystem);
    address = target == null ? null : target.getAddress();
    port = target == null ? null : target.getPort();
  }

  public VertxSqlAddressGroup withDbSystem(@Nullable String dbSystem) {
    return new VertxSqlAddressGroup(endpoints, dbSystem);
  }

  @Nullable
  public String getAddress() {
    return address;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }

  @Nullable
  private static DbServerTarget buildTarget(List<Endpoint> endpoints, @Nullable String dbSystem) {
    if (endpoints.size() == 1 && endpoints.get(0).unixSocket) {
      return DbServerTarget.unixSocket(endpoints.get(0).host);
    }

    DbServerTargetBuilder builder = DbServerTarget.builder(defaultPort(dbSystem));
    for (Endpoint endpoint : endpoints) {
      if (endpoint.unixSocket) {
        return null;
      }
      builder.addEndpoint(endpoint.host, endpoint.port);
    }
    return builder.build();
  }

  private static int defaultPort(@Nullable String dbSystem) {
    if ("postgresql".equals(dbSystem)) {
      return 5432;
    }
    if ("mysql".equals(dbSystem)) {
      return 3306;
    }
    if ("microsoft.sql_server".equals(dbSystem)) {
      return 1433;
    }
    if ("oracle.db".equals(dbSystem)) {
      return 1521;
    }
    if ("ibm.db2".equals(dbSystem)) {
      return 50000;
    }
    return -1;
  }

  private static class Endpoint {
    private final String host;
    private final int port;
    private final boolean unixSocket;

    private Endpoint(String host, int port, boolean unixSocket) {
      this.host = host;
      this.port = port;
      this.unixSocket = unixSocket;
    }

    @Nullable
    private static Endpoint from(@Nullable SqlConnectOptions database) {
      if (database == null || database.getHost() == null) {
        return null;
      }
      String host = database.getHost().trim();
      if (host.isEmpty()) {
        return null;
      }
      boolean unixSocket = host.startsWith("/");
      int configuredPort = database.getPort();
      int port = configuredPort > 0 ? configuredPort : -1;
      DbServerTarget target =
          unixSocket
              ? DbServerTarget.unixSocket(host)
              : DbServerTarget.builder(1).addEndpoint(host, port).build();
      if (target == null) {
        return null;
      }
      return new Endpoint(host, port, unixSocket);
    }
  }
}
