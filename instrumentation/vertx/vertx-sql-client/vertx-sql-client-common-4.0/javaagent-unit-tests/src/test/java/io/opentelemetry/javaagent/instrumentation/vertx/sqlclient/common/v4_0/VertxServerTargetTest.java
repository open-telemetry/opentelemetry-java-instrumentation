/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.vertx.sqlclient.SqlConnectOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VertxServerTargetTest {

  @Test
  void separatesSingleNonDefaultPort() {
    DbServerTarget target =
        VertxServerTarget.from(
            new SqlConnectOptions().setHost("2001:db8::1").setPort(15432), "postgresql");

    assertTarget(target, "2001:db8::1", 15432);
  }

  @ParameterizedTest
  @CsvSource({
    "postgresql, 5432",
    "mysql, 3306",
    "microsoft.sql_server, 1433",
    "oracle.db, 1521",
    "ibm.db2, 50000"
  })
  void omitsKnownDefaultPort(String dbSystemName, int defaultPort) {
    DbServerTarget target =
        VertxServerTarget.from(
            new SqlConnectOptions().setHost("single.example").setPort(defaultPort), dbSystemName);

    assertTarget(target, "single.example", null);
  }

  @Test
  void preservesOrderAndDuplicates() {
    DbServerTarget target =
        VertxServerTarget.from(
            asList(
                options("db-b.example", 15432),
                options("db-a.example", 15432),
                options("db-b.example", 15432)),
            "postgresql");

    assertTarget(target, "db-b.example:15432,db-a.example:15432,db-b.example:15432", null);
  }

  @Test
  void omitsSharedDefaultPorts() {
    DbServerTarget target =
        VertxServerTarget.from(
            asList(options("db-b.example", 5432), options("db-a.example", 5432)), "postgresql");

    assertTarget(target, "db-b.example,db-a.example", null);
  }

  @Test
  void bracketsIpv6WhenPortsAreInline() {
    DbServerTarget target =
        VertxServerTarget.from(
            asList(options("db.example", 6432), options("[2001:db8::1]", 6432)), "postgresql");

    assertTarget(target, "db.example:6432,[2001:db8::1]:6432", null);
  }

  @Test
  void retainsPortsWhenDefaultIsUnknown() {
    DbServerTarget target =
        VertxServerTarget.from(
            asList(options("192.0.2.1", 1234), options("2001:db8::1", 1234)), "other_sql");

    assertTarget(target, "192.0.2.1:1234,[2001:db8::1]:1234", null);
  }

  @Test
  void preservesSingleUnixDomainSocket() {
    DbServerTarget target =
        VertxServerTarget.from(options("/var/run/postgres:primary", 5432), "postgresql");

    assertTarget(target, "/var/run/postgres:primary", null);
  }

  @Test
  void rejectsMultiServerTargetContainingUnixDomainSocket() {
    assertThat(
            VertxServerTarget.from(
                asList(options("/var/run/postgres:primary", 5432), options("2001:db8::1", 5432)),
                "postgresql"))
        .isNull();
  }

  @Test
  void limitsRenderedEndpointsToFive() {
    DbServerTarget target =
        VertxServerTarget.from(
            asList(
                options("db-1.example", 5432),
                options("db-2.example", 5432),
                options("db-3.example", 5432),
                options("db-4.example", 5432),
                options("db-5.example", 5432),
                options("db-6.example", 5432)),
            "postgresql");

    assertTarget(target, "db-1.example,db-2.example,db-3.example,db-4.example,db-5.example", null);
  }

  @Test
  void validatesEndpointsBeyondRenderLimit() {
    assertThat(
            VertxServerTarget.from(
                asList(
                    options("db-1.example", 5432),
                    options("db-2.example", 5432),
                    options("db-3.example", 5432),
                    options("db-4.example", 5432),
                    options("db-5.example", 5432),
                    options("invalid host", 5432)),
                "postgresql"))
        .isNull();
  }

  @Test
  void endpointBeyondRenderLimitControlsPortFormatting() {
    DbServerTarget target =
        VertxServerTarget.from(
            asList(
                options("db-1.example", 5432),
                options("db-2.example", 5432),
                options("db-3.example", 5432),
                options("db-4.example", 5432),
                options("db-5.example", 5432),
                options("db-6.example", 6432)),
            "postgresql");

    assertTarget(
        target,
        "db-1.example:5432,db-2.example:5432,db-3.example:5432,"
            + "db-4.example:5432,db-5.example:5432",
        null);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        " ",
        "host,other",
        "user@host",
        "host?query",
        "host#fragment",
        "host/path",
        "host name",
        "[2001:db8::1",
        "2001:db8::1]"
      })
  void rejectsMalformedHosts(String host) {
    assertThat(VertxServerTarget.from(options(host, 5432), "postgresql")).isNull();
  }

  private static SqlConnectOptions options(String host, int port) {
    return new SqlConnectOptions().setHost(host).setPort(port);
  }

  private static void assertTarget(
      DbServerTarget target, String expectedAddress, Integer expectedPort) {
    assertThat(target.getAddress()).isEqualTo(expectedAddress);
    assertThat(target.getPort()).isEqualTo(expectedPort);
  }
}
