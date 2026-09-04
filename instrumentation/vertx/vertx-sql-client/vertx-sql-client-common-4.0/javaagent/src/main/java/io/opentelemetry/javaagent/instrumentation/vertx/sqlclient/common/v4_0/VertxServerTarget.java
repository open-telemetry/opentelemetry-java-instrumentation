/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MICROSOFT_SQL_SERVER;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MYSQL;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.POSTGRESQL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.IBM_DB2;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.ORACLE_DB;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import io.vertx.sqlclient.SqlConnectOptions;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class VertxServerTarget {

  private final List<Endpoint> endpoints;
  private final boolean complete;
  @Nullable private String address;
  @Nullable private Integer port;

  public static VertxServerTarget create(@Nullable SqlConnectOptions database) {
    List<Endpoint> endpoints = new ArrayList<>(1);
    Endpoint endpoint = Endpoint.from(database);
    if (endpoint != null) {
      endpoints.add(endpoint);
    }
    return new VertxServerTarget(endpoints, endpoint != null);
  }

  public static VertxServerTarget create(
      @Nullable SqlConnectOptions database, @Nullable String dbSystem) {
    VertxServerTarget target = create(database);
    target.resolveDbSystem(dbSystem);
    return target;
  }

  public static VertxServerTarget create(@Nullable List<? extends SqlConnectOptions> databases) {
    if (databases == null || databases.isEmpty()) {
      return new VertxServerTarget(new ArrayList<>(), false);
    }
    List<Endpoint> endpoints = new ArrayList<>(databases.size());
    boolean complete = true;
    for (SqlConnectOptions database : databases) {
      Endpoint endpoint = Endpoint.from(database);
      if (endpoint == null) {
        complete = false;
      } else {
        endpoints.add(endpoint);
      }
    }
    return new VertxServerTarget(endpoints, complete);
  }

  public static VertxServerTarget create(
      @Nullable List<? extends SqlConnectOptions> databases, @Nullable String dbSystem) {
    VertxServerTarget target = create(databases);
    target.resolveDbSystem(dbSystem);
    return target;
  }

  private VertxServerTarget(List<Endpoint> endpoints, boolean complete) {
    this.endpoints = endpoints;
    this.complete = complete;
  }

  public void resolveDbSystem(@Nullable String dbSystem) {
    DbServerTarget target = complete ? buildTarget(endpoints, dbSystem) : null;
    address = target == null ? null : target.getAddress();
    port = target == null ? null : target.getPort();
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
    if (POSTGRESQL.equals(dbSystem)) {
      return 5432;
    }
    if (MYSQL.equals(dbSystem)) {
      return 3306;
    }
    if (MICROSOFT_SQL_SERVER.equals(dbSystem)) {
      return 1433;
    }
    if (ORACLE_DB.equals(dbSystem)) {
      return 1521;
    }
    if (IBM_DB2.equals(dbSystem)) {
      return 50000;
    }
    return -1;
  }

  private static class Endpoint {
    private final String host;
    private final int port;
    private final boolean unixSocket;

    @Nullable
    private static Endpoint from(@Nullable SqlConnectOptions database) {
      if (database == null || database.getHost() == null) {
        return null;
      }
      String host = database.getHost();
      boolean unixSocket = host.startsWith("/");
      int port = database.getPort();
      DbServerTarget target =
          unixSocket
              ? DbServerTarget.unixSocket(host)
              : DbServerTarget.builder(1).addEndpoint(host, port).build();
      if (target == null) {
        return null;
      }
      return new Endpoint(host, port, unixSocket);
    }

    private Endpoint(String host, int port, boolean unixSocket) {
      this.host = host;
      this.port = port;
      this.unixSocket = unixSocket;
    }
  }
}
