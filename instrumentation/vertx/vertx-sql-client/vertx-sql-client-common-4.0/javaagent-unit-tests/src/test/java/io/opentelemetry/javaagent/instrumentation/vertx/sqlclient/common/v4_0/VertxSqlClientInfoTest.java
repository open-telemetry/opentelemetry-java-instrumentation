/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.sqlclient.SqlConnectOptions;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VertxSqlClientInfoTest {

  @Test
  void copiesSingleConfiguration() {
    SqlConnectOptions options = options("db.example", 5432, "database", "user");
    VertxSqlClientInfo info = VertxSqlClientInfo.create(options, "postgresql");

    options.setHost("mutated.example").setPort(15432).setDatabase("other").setUser("other");

    assertInfo(info, "postgresql", "database", "user", "db.example", 5432);
    assertThat(info.getServerTarget().getAddress()).isEqualTo("db.example");
    assertThat(info.getServerTarget().getPort()).isNull();
  }

  @Test
  void copiesListAndUsesConsensusValues() {
    SqlConnectOptions first = options("db-a.example", 5432, "database", "user");
    SqlConnectOptions second = options("db-b.example", 5432, "database", "user");
    List<SqlConnectOptions> options = new ArrayList<>(asList(first, second));
    VertxSqlClientInfo info = VertxSqlClientInfo.create(options, "postgresql");

    first.setHost("mutated.example").setDatabase("other").setUser("other");
    second.setHost("mutated-too.example");
    options.clear();

    assertInfo(info, "postgresql", "database", "user", "db-a.example", 5432);
    assertThat(info.getServerTarget().getAddress()).isEqualTo("db-a.example,db-b.example");
  }

  @Test
  void omitsNonConsensusNamespaceAndUser() {
    VertxSqlClientInfo info =
        VertxSqlClientInfo.create(
            asList(
                options("db-a.example", 5432, "database-a", "user-a"),
                options("db-b.example", 5432, "database-b", "user-b")),
            "postgresql");

    assertThat(info.getNamespace()).isNull();
    assertThat(info.getUser()).isNull();
  }

  @Test
  void distinguishesNotYetCapturedFromUnrepresentableConfiguration() {
    VertxSqlClientInfo notYetCaptured = VertxSqlClientInfo.notYetCaptured("postgresql");
    VertxSqlClientInfo unrepresentable =
        VertxSqlClientInfo.create(options("invalid host", 5432, "database", "user"), "postgresql");

    assertThat(notYetCaptured.isConfigurationCaptured()).isFalse();
    assertThat(notYetCaptured.isServerTargetCaptured()).isFalse();
    assertThat(unrepresentable.isConfigurationCaptured()).isTrue();
    assertThat(unrepresentable.isServerTargetCaptured()).isTrue();
    assertThat(unrepresentable.getServerTarget()).isNull();
  }

  @Test
  void legacySnapshotDoesNotClaimStableTargetCapture() {
    VertxSqlClientInfo info =
        VertxSqlClientInfo.createLegacy(
            options("db.example", 5432, "database", "user"), "postgresql");

    assertThat(info.isConfigurationCaptured()).isTrue();
    assertThat(info.isServerTargetCaptured()).isFalse();
    assertThat(info.getServerTarget()).isNull();
  }

  @Test
  void resolvesDbSystemBeforeSnapshotConstruction() {
    assertThat(
            VertxSqlClientUtil.resolveDbSystemName(
                new SqlConnectOptions(), "io.vertx.pgclient.PgPool"))
        .isEqualTo("postgresql");
  }

  private static SqlConnectOptions options(String host, int port, String database, String user) {
    return new SqlConnectOptions().setHost(host).setPort(port).setDatabase(database).setUser(user);
  }

  private static void assertInfo(
      VertxSqlClientInfo info,
      String dbSystemName,
      String namespace,
      String user,
      String address,
      int port) {
    assertThat(info.getDbSystemName()).isEqualTo(dbSystemName);
    assertThat(info.getNamespace()).isEqualTo(namespace);
    assertThat(info.getUser()).isEqualTo(user);
    assertThat(info.getLegacyServerAddress()).isEqualTo(address);
    assertThat(info.getLegacyServerPort()).isEqualTo(port);
  }
}
