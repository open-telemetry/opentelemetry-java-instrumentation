/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.sqlclient.SqlConnectOptions;
import org.junit.jupiter.api.Test;

class VertxSqlClientRequestTest {

  @Test
  void replacesOneCompleteInfoReference() {
    VertxSqlClientRequest request =
        new VertxSqlClientRequest(
            "select 1", VertxSqlClientInfo.notYetCaptured("postgresql"), false, null);
    VertxSqlClientInfo captured =
        VertxSqlClientInfo.create(
            new SqlConnectOptions()
                .setHost("db.example")
                .setPort(5432)
                .setDatabase("database")
                .setUser("user"),
            "postgresql");

    assertThat(request.replaceInfo(captured)).isTrue();
    assertThat(request.getDatabase()).isEqualTo("database");
    assertThat(request.getUser()).isEqualTo("user");
    assertThat(request.getConfiguredServerAddress()).isEqualTo("db.example");
    assertThat(request.isInfoUpdated()).isTrue();
  }

  @Test
  void doesNotReplaceCapturedButUnrepresentableInfo() {
    VertxSqlClientInfo unrepresentable =
        VertxSqlClientInfo.create(
            new SqlConnectOptions().setHost("invalid host").setPort(5432), "postgresql");
    VertxSqlClientRequest request =
        new VertxSqlClientRequest("select 1", unrepresentable, false, null);
    VertxSqlClientInfo replacement =
        VertxSqlClientInfo.create(
            new SqlConnectOptions().setHost("db.example").setPort(5432), "postgresql");

    assertThat(request.replaceInfo(replacement)).isFalse();
    assertThat(request.getHost()).isEqualTo("invalid host");
    assertThat(request.getConfiguredServerAddress()).isNull();
    assertThat(request.isInfoUpdated()).isFalse();
  }
}
