/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

import io.opentelemetry.context.Context;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DbExecution {
  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String OTHER_SQL = "other_sql";

  private final String system;
  private final String user;
  private final String name;
  private final String host;
  private final Integer port;
  private final String connectionString;
  private final String rawQueryText;

  private Context context;

  public DbExecution(QueryExecutionInfo queryInfo, ConnectionFactoryOptions factoryOptions) {
    Connection originalConnection = queryInfo.getConnectionInfo().getOriginalConnection();
    this.system =
        originalConnection != null
            ? originalConnection
                .getMetadata()
                .getDatabaseProductName()
                .toLowerCase(Locale.ROOT)
                .split(" ")[0]
            : OTHER_SQL;
    this.user = factoryOptions.hasOption(USER) ? (String) factoryOptions.getValue(USER) : null;
    this.name =
        factoryOptions.hasOption(DATABASE)
            ? ((String) factoryOptions.getValue(DATABASE)).toLowerCase(Locale.ROOT)
            : null;
    String driver =
        factoryOptions.hasOption(DRIVER) ? (String) factoryOptions.getValue(DRIVER) : null;
    String protocol =
        factoryOptions.hasOption(PROTOCOL) ? (String) factoryOptions.getValue(PROTOCOL) : null;
    this.host = factoryOptions.hasOption(HOST) ? (String) factoryOptions.getValue(HOST) : null;
    this.port = factoryOptions.hasOption(PORT) ? (Integer) factoryOptions.getValue(PORT) : null;
    this.connectionString =
        String.format(
            "%s%s:%s%s",
            driver != null ? driver : "",
            protocol != null ? ":" + protocol : "",
            host != null ? "//" + host : "",
            port != null ? ":" + port : "");
    this.rawQueryText =
        queryInfo.getQueries().stream().map(QueryInfo::getQuery).collect(Collectors.joining(";\n"));
  }

  public Integer getPort() {
    return port;
  }

  public String getHost() {
    return host;
  }

  public String getSystem() {
    return system;
  }

  public String getUser() {
    return user;
  }

  public String getName() {
    return name;
  }

  public String getConnectionString() {
    return connectionString;
  }

  public String getRawQueryText() {
    return rawQueryText;
  }

  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  @Override
  public String toString() {
    return "DbExecution{"
        + "system='"
        + system
        + '\''
        + ", user='"
        + user
        + '\''
        + ", name='"
        + name
        + '\''
        + ", host='"
        + host
        + '\''
        + ", port="
        + port
        + ", connectionString='"
        + connectionString
        + '\''
        + ", rawStatement='"
        + rawQueryText
        + '\''
        + ", context="
        + context
        + '}';
  }
}
