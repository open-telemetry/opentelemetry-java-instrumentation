/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.sqlclient.SqlConnectOptions;
import org.junit.jupiter.api.Test;

class VertxSqlClientDeferredRequestTest {

  @Test
  void replacesOneCompleteInfoReference() {
    VertxSqlClientInfo initialInfo = VertxSqlClientInfo.createUnknown("postgresql");
    VertxSqlClientDeferredRequest request =
        new VertxSqlClientDeferredRequest("select 1", initialInfo, true, 2L);
    VertxSqlClientInfo captured =
        VertxSqlClientInfo.create(
            new SqlConnectOptions()
                .setHost("db.example")
                .setPort(15432)
                .setDatabase("database")
                .setUser("user"),
            "postgresql");

    assertThat(request.getInfo()).isSameAs(initialInfo);
    assertThat(request.isInfoUpdated()).isFalse();
    assertThat(request.replaceInfo(captured)).isTrue();
    assertThat(request.getInfo()).isSameAs(captured);
    assertThat(request.getDatabase()).isEqualTo("database");
    assertThat(request.getUser()).isEqualTo("user");
    assertThat(request.getHost()).isEqualTo("db.example");
    assertThat(request.getPort()).isEqualTo(15432);
    assertThat(request.getConfiguredServerAddress()).isEqualTo("db.example");
    assertThat(request.getConfiguredServerPort()).isEqualTo(15432);
    assertThat(request.getDbSystemName()).isEqualTo("postgresql");
    assertThat(request.getQueryText()).isEqualTo("select 1");
    assertThat(request.isParameterizedQuery()).isTrue();
    assertThat(request.getOperationBatchSize()).isEqualTo(2L);
    assertThat(request.isInfoUpdated()).isTrue();
    assertThat(request.replaceInfo(initialInfo)).isFalse();
    assertThat(request.getInfo()).isSameAs(captured);
    assertThat(request.freezeInfo()).isTrue();
  }

  @Test
  void countsAnUnrepresentableSnapshotAsCaptured() {
    VertxSqlClientDeferredRequest request =
        new VertxSqlClientDeferredRequest(
            "select 1", VertxSqlClientInfo.createUnknown("postgresql"), false, null);
    VertxSqlClientInfo unrepresentable =
        VertxSqlClientInfo.create(
            new SqlConnectOptions().setHost("invalid host").setPort(5432), "postgresql");
    VertxSqlClientInfo replacement =
        VertxSqlClientInfo.create(
            new SqlConnectOptions().setHost("db.example").setPort(5432), "postgresql");

    assertThat(request.replaceInfo(unrepresentable)).isTrue();
    assertThat(request.replaceInfo(replacement)).isFalse();
    assertThat(request.getHost()).isEqualTo("invalid host");
    assertThat(request.getConfiguredServerAddress()).isNull();
    assertThat(request.getConfiguredServerPort()).isNull();
    assertThat(request.isInfoUpdated()).isTrue();
    assertThat(request.freezeInfo()).isTrue();
  }

  @Test
  void doesNotReplaceInfoAfterRequestIsFrozen() {
    VertxSqlClientDeferredRequest request =
        new VertxSqlClientDeferredRequest(
            "select 1", VertxSqlClientInfo.createUnknown("postgresql"), false, null);

    assertThat(request.freezeInfo()).isFalse();
    assertThat(
            request.replaceInfo(
                VertxSqlClientInfo.create(
                    new SqlConnectOptions().setHost("db.example").setPort(5432), "postgresql")))
        .isFalse();
    assertThat(request.isInfoUpdated()).isFalse();
    assertThat(request.getConfiguredServerAddress()).isNull();
  }
}
