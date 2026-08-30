/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.sqlclient.SqlConnectOptions;
import org.junit.jupiter.api.Test;

class VertxSqlAddressGroupTest {

  @Test
  void preservesSingleUnixDomainSocketWithoutPort() {
    VertxSqlAddressGroup addressGroup =
        VertxSqlAddressGroup.of(
            new SqlConnectOptions().setHost("/var/run/postgres:primary").setPort(5432));

    assertThat(addressGroup.getAddress()).isEqualTo("/var/run/postgres:primary");
    assertThat(addressGroup.getPort()).isNull();
  }

  @Test
  void preservesUnixDomainSocketWithinAddressGroupWithoutPort() {
    VertxSqlAddressGroup addressGroup =
        VertxSqlAddressGroup.of(
            asList(
                new SqlConnectOptions().setHost("db.example").setPort(5432),
                new SqlConnectOptions().setHost("/var/run/postgres:primary").setPort(5433),
                new SqlConnectOptions().setHost("2001:db8::1").setPort(5434)));

    assertThat(addressGroup.getAddress())
        .isEqualTo("db.example:5432,/var/run/postgres:primary,[2001:db8::1]:5434");
    assertThat(addressGroup.getPort()).isNull();
  }
}
