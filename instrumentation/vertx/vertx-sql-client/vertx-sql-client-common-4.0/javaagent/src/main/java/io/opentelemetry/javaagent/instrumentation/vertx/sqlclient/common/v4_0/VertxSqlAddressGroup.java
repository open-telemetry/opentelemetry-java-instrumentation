/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import io.vertx.sqlclient.SqlConnectOptions;
import java.util.List;
import javax.annotation.Nullable;

public class VertxSqlAddressGroup {

  private final String address;
  @Nullable private final Integer port;

  @Nullable
  public static VertxSqlAddressGroup of(@Nullable SqlConnectOptions database) {
    if (database == null || database.getHost() == null) {
      return null;
    }
    String host = database.getHost();
    return new VertxSqlAddressGroup(host, host.startsWith("/") ? null : database.getPort());
  }

  @Nullable
  public static VertxSqlAddressGroup of(@Nullable List<? extends SqlConnectOptions> databases) {
    if (databases == null || databases.isEmpty()) {
      return null;
    }
    if (databases.size() == 1) {
      return of(databases.get(0));
    }
    StringBuilder address = new StringBuilder();
    for (SqlConnectOptions database : databases) {
      if (database == null) {
        return null;
      }
      String host = database.getHost();
      if (host == null) {
        return null;
      }
      if (address.length() > 0) {
        address.append(',');
      }
      appendHostPort(address, host, database.getPort());
    }
    return new VertxSqlAddressGroup(address.toString(), null);
  }

  private VertxSqlAddressGroup(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  public String getAddress() {
    return address;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }

  private static void appendHostPort(StringBuilder address, String host, int port) {
    if (host.startsWith("/")) {
      address.append(host);
      return;
    }
    if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
      address.append('[').append(host).append(']');
    } else {
      address.append(host);
    }
    address.append(':').append(port);
  }
}
