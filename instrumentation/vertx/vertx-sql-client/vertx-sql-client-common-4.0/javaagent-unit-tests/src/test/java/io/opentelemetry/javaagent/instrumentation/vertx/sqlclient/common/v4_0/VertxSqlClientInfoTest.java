/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
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
    VertxSqlClientRequest request = new VertxSqlClientRequest("select 1", info, false, null);

    options.setHost("mutated.example").setPort(15432).setDatabase("other").setUser("other");

    assertInfo(info, "postgresql", "database", "user", "db.example", 5432);
    assertThat(info.getServerTarget().getAddress()).isEqualTo("db.example");
    assertThat(info.getServerTarget().getPort()).isNull();
    assertThat(request.getDatabase()).isEqualTo("database");
    assertThat(request.getUser()).isEqualTo("user");
    assertThat(request.getHost()).isEqualTo("db.example");
    assertThat(request.getPort()).isEqualTo(5432);
    assertThat(request.getConfiguredServerAddress()).isEqualTo("db.example");
    assertThat(request.getConfiguredServerPort()).isNull();
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
  void capturesUnrepresentableConfigurationWithoutFallingBackToLegacyTarget() {
    VertxSqlClientInfo unrepresentable =
        VertxSqlClientInfo.create(options("invalid host", 5432, "database", "user"), "postgresql");

    assertThat(unrepresentable.getServerTarget()).isNull();
    VertxSqlClientRequest request =
        new VertxSqlClientRequest("select 1", unrepresentable, false, null);
    assertThat(request.getHost()).isEqualTo("invalid host");
    assertThat(request.getPort()).isEqualTo(5432);
    assertThat(request.getConfiguredServerAddress()).isNull();
    assertThat(request.getConfiguredServerPort()).isNull();
    VertxSqlClientAttributesGetter getter = new VertxSqlClientAttributesGetter();
    assertThat(getter.getServerAddress(request))
        .isEqualTo(emitStableDatabaseSemconv() ? null : "invalid host");
    assertThat(getter.getServerPort(request)).isEqualTo(emitStableDatabaseSemconv() ? null : 5432);
  }

  @Test
  void unknownInfoContainsOnlyTheDatabaseSystem() {
    VertxSqlClientInfo info = VertxSqlClientInfo.createUnknown("postgresql");

    assertThat(info.getDbSystemName()).isEqualTo("postgresql");
    assertThat(info.getNamespace()).isNull();
    assertThat(info.getUser()).isNull();
    assertThat(info.getLegacyServerAddress()).isNull();
    assertThat(info.getLegacyServerPort()).isNull();
    assertThat(info.getServerTarget()).isNull();
    assertThat(VertxSqlClientInfo.createUnknown(null).getDbSystemName()).isEqualTo("other_sql");
  }

  @Test
  void newSnapshotsDoNotChangeExistingRequests() {
    SqlConnectOptions options = options("db.example", 5432, "database", "user");
    VertxSqlClientInfo initialInfo = VertxSqlClientInfo.create(options, null);
    VertxSqlClientRequest request = new VertxSqlClientRequest("select 1", initialInfo, false, null);

    VertxSqlClientInfo resolved = VertxSqlClientInfo.create(options, "postgresql");
    VertxSqlClientRequest resolvedRequest =
        new VertxSqlClientRequest("select 1", resolved, false, null);

    assertThat(resolvedRequest.getInfo()).isSameAs(resolved);
    assertThat(resolvedRequest.getDbSystemName()).isEqualTo("postgresql");
    assertThat(resolvedRequest.getConfiguredServerPort()).isNull();
    assertThat(request.getInfo()).isSameAs(initialInfo);
    assertThat(request.getDbSystemName()).isEqualTo("other_sql");
    assertThat(request.getConfiguredServerPort()).isEqualTo(5432);
  }

  @Test
  void missingOptionsDoNotCreateSnapshots() {
    assertThat(VertxSqlClientInfo.create((SqlConnectOptions) null, "postgresql")).isNull();
    assertThat(VertxSqlClientInfo.create((List<SqlConnectOptions>) null, "postgresql")).isNull();
  }

  @Test
  void resolvesDbSystemWithoutOptions() {
    assertThat(VertxSqlClientUtil.resolveDbSystemName(null, "io.vertx.pgclient.PgPool"))
        .isEqualTo("postgresql");
    assertThat(VertxSqlClientUtil.resolveDbSystemName(null, null)).isEqualTo("other_sql");
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
