/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.util.stream.Collectors;

public class DbExecution {
  private final String system;
  private final String user;
  private final String name;
  private final String host;
  private final Integer port;
  private final String connectionString;
  private final String rawStatement;

  private Context context;
  private Scope scope;

  public DbExecution(QueryExecutionInfo queryInfo, ConnectionFactoryOptions factoryOptions) {
    Connection originalConnection = queryInfo.getConnectionInfo().getOriginalConnection();
    this.system =
        originalConnection != null
            ? originalConnection.getMetadata().getDatabaseProductName().toLowerCase().split(" ")[0]
            : "";
    this.user = factoryOptions.hasOption(USER) ? (String) factoryOptions.getValue(USER) : null;
    this.name =
        factoryOptions.hasOption(DATABASE)
            ? ((String) factoryOptions.getValue(DATABASE)).toLowerCase()
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
    this.rawStatement =
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

  public String getRawStatement() {
    return rawStatement;
  }

  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  public Scope getScope() {
    return scope;
  }

  public void setScope(Scope scope) {
    this.scope = scope;
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
        + rawStatement
        + '\''
        + ", context="
        + context
        + ", scope="
        + scope
        + '}';
  }
}
