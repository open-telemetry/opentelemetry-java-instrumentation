/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import io.vertx.sqlclient.SqlConnectOptions;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The complete configured target of a client that was given more than one server, rendered as a
 * comma separated {@code host:port} list, e.g. {@code h1:5432,h2:5432}.
 *
 * <p>The rendering is done once, when the client is configured, and the result is immutable.
 */
public final class VertxSqlAddressGroup {

  private final String address;

  private VertxSqlAddressGroup(String address) {
    this.address = address;
  }

  /**
   * The target of {@code databases}, or null when they name a single server and there is no group.
   */
  @Nullable
  public static VertxSqlAddressGroup of(@Nullable List<? extends SqlConnectOptions> databases) {
    if (databases == null || databases.size() < 2) {
      return null;
    }
    StringBuilder address = new StringBuilder();
    for (SqlConnectOptions database : databases) {
      if (address.length() > 0) {
        address.append(',');
      }
      appendHostPort(address, database);
    }
    return new VertxSqlAddressGroup(address.toString());
  }

  private static void appendHostPort(StringBuilder address, SqlConnectOptions database) {
    String host = database.getHost();
    // a literal IPv6 address is bracketed so that the port stays unambiguous; a Unix domain socket
    // path is kept as configured
    if (host != null && host.indexOf(':') >= 0 && !host.startsWith("[")) {
      address.append('[').append(host).append(']');
    } else {
      address.append(host);
    }
    address.append(':').append(database.getPort());
  }

  public String getAddress() {
    return address;
  }
}
