/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.sqlclient.SqlConnectOptions;
import org.junit.jupiter.api.Test;

class VertxSqlClientDataTest {

  @Test
  void retainsValuesSharedByAllServers() {
    VertxSqlClientData data =
        requireNonNull(
            VertxSqlClientData.create(
                asList(
                    options("db-a.example", "customers", "app"),
                    options("db-b.example", "customers", "app"))));
    data.resolveDbSystem("postgresql");

    assertThat(data.getDatabase()).isEqualTo("customers");
    assertThat(data.getUser()).isEqualTo("app");
    assertThat(data.getHost()).isEqualTo("db-a.example");
    assertThat(data.getConfiguredServerAddress()).isEqualTo("db-a.example,db-b.example");
  }

  @Test
  void omitsValuesThatDifferBetweenServers() {
    VertxSqlClientData data =
        requireNonNull(
            VertxSqlClientData.create(
                asList(
                    options("db-a.example", "customers", "app"),
                    options("db-b.example", "orders", "reporter"))));
    data.resolveDbSystem("postgresql");

    assertThat(data.getDatabase()).isNull();
    assertThat(data.getUser()).isNull();
    assertThat(data.getConfiguredServerAddress()).isEqualTo("db-a.example,db-b.example");
  }

  @Test
  void acceptsSharedNullValues() {
    VertxSqlClientData data =
        requireNonNull(
            VertxSqlClientData.create(
                asList(options("db-a.example", null, null), options("db-b.example", null, null))));
    data.resolveDbSystem("postgresql");

    assertThat(data.getDatabase()).isNull();
    assertThat(data.getUser()).isNull();
    assertThat(data.getConfiguredServerAddress()).isEqualTo("db-a.example,db-b.example");
  }

  @Test
  void snapshotsValuesBeforeOptionsAreMutated() {
    SqlConnectOptions options = options("db.example", "customers", "app");
    VertxSqlClientData data = requireNonNull(VertxSqlClientData.create(options));
    options.setHost("mutated.example").setDatabase("orders").setUser("reporter");
    data.resolveDbSystem("postgresql");

    assertThat(data.getDatabase()).isEqualTo("customers");
    assertThat(data.getUser()).isEqualTo("app");
    assertThat(data.getHost()).isEqualTo("db.example");
    assertThat(data.getConfiguredServerAddress()).isEqualTo("db.example");
  }

  private static SqlConnectOptions options(String host, String database, String user) {
    SqlConnectOptions options = new SqlConnectOptions().setHost(host).setPort(5432);
    if (database != null) {
      options.setDatabase(database);
    }
    if (user != null) {
      options.setUser(user);
    }
    return options;
  }
}
