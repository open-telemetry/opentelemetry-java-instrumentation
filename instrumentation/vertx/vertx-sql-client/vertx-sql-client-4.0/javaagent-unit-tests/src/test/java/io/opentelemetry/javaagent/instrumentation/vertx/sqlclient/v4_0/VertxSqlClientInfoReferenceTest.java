/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientRequest;
import io.vertx.sqlclient.SqlConnectOptions;
import org.junit.jupiter.api.Test;

class VertxSqlClientInfoReferenceTest {

  @Test
  void constructionSnapshotUpdatesDoNotChangeExistingRequests() {
    SqlConnectOptions options =
        new SqlConnectOptions()
            .setHost("db.example")
            .setPort(5432)
            .setDatabase("database")
            .setUser("user");
    VertxSqlClientInfoReference reference =
        new VertxSqlClientInfoReference(VertxSqlClientInfo.create(options, null));
    VertxSqlClientRequest request =
        new VertxSqlClientRequest("select 1", reference.get(), false, null);
    VertxSqlClientInfo resolved = VertxSqlClientInfo.create(options, "postgresql");

    reference.set(resolved);

    assertThat(reference.get()).isSameAs(resolved);
    VertxSqlClientRequest laterRequest =
        new VertxSqlClientRequest("select 2", reference.get(), false, null);
    assertThat(laterRequest.getDbSystemName()).isEqualTo("postgresql");
    assertThat(laterRequest.getConfiguredServerPort()).isNull();
    assertThat(request.getDbSystemName()).isEqualTo("other_sql");
    assertThat(request.getConfiguredServerPort()).isEqualTo(5432);
  }

  @Test
  void supportsMissingMetadata() {
    VertxSqlClientInfoReference reference = new VertxSqlClientInfoReference(null);
    assertThat(reference.get()).isNull();

    VertxSqlClientInfo info = VertxSqlClientInfo.create(new SqlConnectOptions(), "postgresql");
    reference.set(info);
    assertThat(reference.get()).isSameAs(info);

    reference.set(null);
    assertThat(reference.get()).isNull();
  }
}
