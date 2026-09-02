/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import io.vertx.sqlclient.SqlConnectOptions;
import javax.annotation.Nullable;

public class VertxSqlClientData implements VertxSqlClientDataProvider {
  @Nullable private final SqlConnectOptions connectOptions;
  @Nullable private final String dbSystem;
  @Nullable private final VertxSqlAddressGroup addressGroup;

  public static VertxSqlClientData fromSuppliedConnectOptions(
      SqlConnectOptions connectOptions, @Nullable String dbSystem) {
    SqlConnectOptions copiedOptions = new SqlConnectOptions(connectOptions);
    return new VertxSqlClientData(copiedOptions, dbSystem, null);
  }

  public VertxSqlClientData(
      @Nullable SqlConnectOptions connectOptions,
      @Nullable String dbSystem,
      @Nullable VertxSqlAddressGroup addressGroup) {
    this.connectOptions = connectOptions;
    this.dbSystem = dbSystem;
    this.addressGroup = addressGroup;
  }

  @Nullable
  public SqlConnectOptions getConnectOptions() {
    return connectOptions;
  }

  @Nullable
  public String getDbSystem() {
    return dbSystem;
  }

  @Nullable
  public VertxSqlAddressGroup getAddressGroup() {
    return addressGroup;
  }

  @Override
  public VertxSqlClientData get() {
    return this;
  }
}
