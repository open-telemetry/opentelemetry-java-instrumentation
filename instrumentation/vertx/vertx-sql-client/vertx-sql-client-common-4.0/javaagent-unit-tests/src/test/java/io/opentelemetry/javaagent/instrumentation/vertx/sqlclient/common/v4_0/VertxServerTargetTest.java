/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.sqlclient.SqlConnectOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VertxServerTargetTest {

  @Test
  void separatesSingleKnownNonDefaultPort() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            new SqlConnectOptions().setHost("2001:db8::1").setPort(15432), "postgresql");

    assertThat(serverTarget.getAddress()).isEqualTo("2001:db8::1");
    assertThat(serverTarget.getPort()).isEqualTo(15432);
  }

  @ParameterizedTest
  @CsvSource({
    "postgresql, 5432",
    "mysql, 3306",
    "microsoft.sql_server, 1433",
    "oracle.db, 1521",
    "ibm.db2, 50000"
  })
  void omitsKnownDefaultPortFromSingleAddress(String dbSystem, int defaultPort) {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            new SqlConnectOptions().setHost("single.example").setPort(defaultPort), dbSystem);

    assertThat(serverTarget.getAddress()).isEqualTo("single.example");
    assertThat(serverTarget.getPort()).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "postgresql, 5432",
    "mysql, 3306",
    "microsoft.sql_server, 1433",
    "oracle.db, 1521",
    "ibm.db2, 50000"
  })
  void omitsKnownDefaultPortsAndPreservesConfiguredOrder(String dbSystem, int defaultPort) {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            asList(
                new SqlConnectOptions().setHost("db-b.example").setPort(defaultPort),
                new SqlConnectOptions().setHost("db-a.example").setPort(defaultPort)),
            dbSystem);

    assertThat(serverTarget.getAddress()).isEqualTo("db-b.example,db-a.example");
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void inlinesSharedNonDefaultPortWhilePreservingOrderAndDuplicates() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            asList(
                new SqlConnectOptions().setHost("db-b.example").setPort(15432),
                new SqlConnectOptions().setHost("db-a.example").setPort(15432),
                new SqlConnectOptions().setHost("db-b.example").setPort(15432)),
            "postgresql");

    assertThat(serverTarget.getAddress())
        .isEqualTo("db-b.example:15432,db-a.example:15432,db-b.example:15432");
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void inlinesSharedNonDefaultPortAndBracketsIpv6() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            asList(
                new SqlConnectOptions().setHost("db.example").setPort(6432),
                new SqlConnectOptions().setHost("[2001:db8::1]").setPort(6432)),
            "postgresql");

    assertThat(serverTarget.getAddress()).isEqualTo("db.example:6432,[2001:db8::1]:6432");
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void inlinesMixedEffectivePortsAndBracketsIpv6() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            asList(
                new SqlConnectOptions().setHost("db-b.example").setPort(5432),
                new SqlConnectOptions().setHost("2001:db8::1").setPort(15432)),
            "postgresql");

    assertThat(serverTarget.getAddress()).isEqualTo("db-b.example:5432,[2001:db8::1]:15432");
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void retainsPortsWhenTheDefaultIsUnknown() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            asList(
                new SqlConnectOptions().setHost("192.0.2.1").setPort(1234),
                new SqlConnectOptions().setHost("2001:db8::1").setPort(1234)),
            "other_sql");

    assertThat(serverTarget.getAddress()).isEqualTo("192.0.2.1:1234,[2001:db8::1]:1234");
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void retainsSinglePortWhenTheDefaultIsUnknown() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            new SqlConnectOptions().setHost("discovery.example").setPort(1234), "other_sql");

    assertThat(serverTarget.getAddress()).isEqualTo("discovery.example");
    assertThat(serverTarget.getPort()).isEqualTo(1234);
  }

  @Test
  void preservesSingleUnixDomainSocketWithoutPort() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            new SqlConnectOptions().setHost("/var/run/postgres:primary").setPort(5432),
            "postgresql");

    assertThat(serverTarget.getAddress()).isEqualTo("/var/run/postgres:primary");
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void preservesUnixDomainSocketWhitespace() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            new SqlConnectOptions().setHost("/var/run/postgres ").setPort(5432), "postgresql");

    assertThat(serverTarget.getAddress()).isEqualTo("/var/run/postgres ");
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void rejectsWhitespacePrefixedUnixDomainSocket() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            new SqlConnectOptions().setHost(" /var/run/postgres").setPort(5432), "postgresql");

    assertThat(serverTarget.getAddress()).isNull();
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void rejectsZeroPort() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            new SqlConnectOptions().setHost("db.example").setPort(0), "postgresql");

    assertThat(serverTarget.getAddress()).isNull();
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void omitsAddressAndPortForTargetContainingUnixDomainSocket() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            asList(
                new SqlConnectOptions().setHost("/var/run/postgres:primary").setPort(5433),
                new SqlConnectOptions().setHost("2001:db8::1").setPort(5432)),
            "postgresql");

    assertThat(serverTarget.getAddress()).isNull();
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void includesExactlyFiveEndpoints() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            asList(
                new SqlConnectOptions().setHost("db-1.example").setPort(5432),
                new SqlConnectOptions().setHost("db-2.example").setPort(5432),
                new SqlConnectOptions().setHost("db-3.example").setPort(5432),
                new SqlConnectOptions().setHost("db-4.example").setPort(5432),
                new SqlConnectOptions().setHost("db-5.example").setPort(5432)),
            "postgresql");

    assertThat(serverTarget.getAddress())
        .isEqualTo("db-1.example,db-2.example,db-3.example,db-4.example,db-5.example");
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void includesOnlyTheFirstFiveOfSixEndpoints() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            asList(
                new SqlConnectOptions().setHost("db-1.example").setPort(5432),
                new SqlConnectOptions().setHost("db-2.example").setPort(5432),
                new SqlConnectOptions().setHost("db-3.example").setPort(5432),
                new SqlConnectOptions().setHost("db-4.example").setPort(5432),
                new SqlConnectOptions().setHost("db-5.example").setPort(5432),
                new SqlConnectOptions().setHost("db-6.example").setPort(5432)),
            "postgresql");

    assertThat(serverTarget.getAddress())
        .isEqualTo("db-1.example,db-2.example,db-3.example,db-4.example,db-5.example");
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void positionSixNonDefaultPortControlsFormatting() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            asList(
                new SqlConnectOptions().setHost("db-1.example").setPort(5432),
                new SqlConnectOptions().setHost("db-2.example").setPort(5432),
                new SqlConnectOptions().setHost("db-3.example").setPort(5432),
                new SqlConnectOptions().setHost("db-4.example").setPort(5432),
                new SqlConnectOptions().setHost("db-5.example").setPort(5432),
                new SqlConnectOptions().setHost("db-6.example").setPort(6432)),
            "postgresql");

    assertThat(serverTarget.getAddress())
        .isEqualTo(
            "db-1.example:5432,db-2.example:5432,db-3.example:5432,"
                + "db-4.example:5432,db-5.example:5432");
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void rejectsInvalidEndpointAtPositionSix() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            asList(
                new SqlConnectOptions().setHost("db-1.example").setPort(5432),
                new SqlConnectOptions().setHost("db-2.example").setPort(5432),
                new SqlConnectOptions().setHost("db-3.example").setPort(5432),
                new SqlConnectOptions().setHost("db-4.example").setPort(5432),
                new SqlConnectOptions().setHost("db-5.example").setPort(5432),
                new SqlConnectOptions().setHost("invalid host").setPort(5432)),
            "postgresql");

    assertThat(serverTarget.getAddress()).isNull();
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void omitsAddressAndPortForUnixDomainSocketAtPositionSix() {
    VertxServerTarget serverTarget =
        VertxServerTarget.create(
            asList(
                new SqlConnectOptions().setHost("db-1.example").setPort(5432),
                new SqlConnectOptions().setHost("db-2.example").setPort(5432),
                new SqlConnectOptions().setHost("db-3.example").setPort(5432),
                new SqlConnectOptions().setHost("db-4.example").setPort(5432),
                new SqlConnectOptions().setHost("db-5.example").setPort(5432),
                new SqlConnectOptions().setHost("/var/run/postgres").setPort(5432)),
            "postgresql");

    assertThat(serverTarget.getAddress()).isNull();
    assertThat(serverTarget.getPort()).isNull();
  }

  @Test
  void canNormalizeAStoredSnapshotOnceTheDriverIsKnown() {
    SqlConnectOptions options = new SqlConnectOptions().setHost("db.example").setPort(5432);
    VertxServerTarget serverTarget = VertxServerTarget.create(options);
    options.setHost("mutated.example").setPort(15432);

    serverTarget.resolveDbSystem("postgresql");

    assertThat(serverTarget.getAddress()).isEqualTo("db.example");
    assertThat(serverTarget.getPort()).isNull();
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
    VertxServerTarget serverTarget =
        VertxServerTarget.create(new SqlConnectOptions().setHost(host).setPort(5432), "postgresql");

    assertThat(serverTarget.getAddress()).isNull();
    assertThat(serverTarget.getPort()).isNull();
  }
}
