/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.internal.SqlClientBase;
import io.vertx.sqlclient.internal.SqlConnectionBase;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public final class VertxSqlClientConstructionState {
  private final List<SqlConnectOptions> databases;
  private final List<SqlClientBase> clients = new ArrayList<>();
  @Nullable private VertxSqlClientInfo info;

  public VertxSqlClientConstructionState(List<SqlConnectOptions> databases, String dbSystemName) {
    this.databases = databases;
    updateInfo(dbSystemName);
  }

  @Nullable
  public VertxSqlClientInfo getInfo() {
    return info;
  }

  public void setDbSystemName(String dbSystemName) {
    if (info == null || !VertxSqlClientUtil.isKnownDbSystem(info.getDbSystemName())) {
      updateInfo(dbSystemName);
    }
  }

  private void updateInfo(String dbSystemName) {
    info = VertxSqlClientInfo.create(databases, dbSystemName);
  }

  public void attachClient(SqlClientBase client) {
    if (!(client instanceof SqlConnectionBase)) {
      clients.add(client);
    }
    publish(client);
  }

  public void complete(@Nullable Object client) {
    for (SqlClientBase constructedClient : clients) {
      publish(constructedClient);
    }
    if (client instanceof Pool) {
      VertxSqlClientSingletons.setPoolInfo((Pool) client, info);
    }
  }

  private void publish(SqlClientBase client) {
    VertxSqlClientSingletons.attachClientInfo(client, info);
  }
}
