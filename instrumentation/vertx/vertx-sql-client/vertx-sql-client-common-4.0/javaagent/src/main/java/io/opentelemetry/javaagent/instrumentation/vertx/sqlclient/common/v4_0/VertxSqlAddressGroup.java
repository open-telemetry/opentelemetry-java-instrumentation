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

  private VertxSqlAddressGroup(String address) {
    this.address = address;
  }

  @Nullable
  public static VertxSqlAddressGroup of(@Nullable List<? extends SqlConnectOptions> databases) {
    if (databases == null || databases.size() < 2) {
      return null;
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
    return new VertxSqlAddressGroup(address.toString());
  }

  private static void appendHostPort(StringBuilder address, String host, int port) {
    // Bracket IPv6 literals; leave Unix socket paths unchanged.
    if (host.indexOf(':') >= 0 && !host.startsWith("[") && !host.startsWith("/")) {
      address.append('[').append(host).append(']');
    } else {
      address.append(host);
    }
    address.append(':').append(port);
  }

  public String getAddress() {
    return address;
  }
}
