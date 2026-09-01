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

class VertxSqlAddressGroupTest {

  @Test
  void separatesSingleKnownNonDefaultPort() {
    VertxSqlAddressGroup addressGroup =
        VertxSqlAddressGroup.of(
            new SqlConnectOptions().setHost("2001:db8::1").setPort(15432), "postgresql");

    assertThat(addressGroup.getAddress()).isEqualTo("2001:db8::1");
    assertThat(addressGroup.getPort()).isEqualTo(15432);
  }

  @ParameterizedTest
  @CsvSource({
    "postgresql, 5432",
    "mysql, 3306",
    "microsoft.sql_server, 1433",
    "oracle.db, 1521",
    "ibm.db2, 50000"
  })
  void omitsKnownDefaultPorts(String dbSystem, int defaultPort) {
    VertxSqlAddressGroup singleAddress =
        VertxSqlAddressGroup.of(
            new SqlConnectOptions().setHost("single.example").setPort(defaultPort), dbSystem);
    VertxSqlAddressGroup addressGroup =
        VertxSqlAddressGroup.of(
            asList(
                new SqlConnectOptions().setHost("db-b.example").setPort(defaultPort),
                new SqlConnectOptions().setHost("db-a.example").setPort(defaultPort)),
            dbSystem);

    assertThat(singleAddress.getAddress()).isEqualTo("single.example");
    assertThat(singleAddress.getPort()).isNull();
    assertThat(addressGroup.getAddress()).isEqualTo("db-b.example,db-a.example");
    assertThat(addressGroup.getPort()).isNull();
  }

  @Test
  void extractsSharedNonDefaultPortWhilePreservingOrderAndDuplicates() {
    VertxSqlAddressGroup addressGroup =
        VertxSqlAddressGroup.of(
            asList(
                new SqlConnectOptions().setHost("db-b.example").setPort(15432),
                new SqlConnectOptions().setHost("db-a.example").setPort(15432),
                new SqlConnectOptions().setHost("db-b.example").setPort(15432)),
            "postgresql");

    assertThat(addressGroup.getAddress()).isEqualTo("db-b.example,db-a.example,db-b.example");
    assertThat(addressGroup.getPort()).isEqualTo(15432);
  }

  @Test
  void extractsSharedNonDefaultPortWithoutBracketingIpv6() {
    VertxSqlAddressGroup addressGroup =
        VertxSqlAddressGroup.of(
            asList(
                new SqlConnectOptions().setHost("db.example").setPort(6432),
                new SqlConnectOptions().setHost("[2001:db8::1]").setPort(6432)),
            "postgresql");

    assertThat(addressGroup.getAddress()).isEqualTo("db.example,2001:db8::1");
    assertThat(addressGroup.getPort()).isEqualTo(6432);
  }

  @Test
  void inlinesMixedEffectivePortsAndBracketsIpv6() {
    VertxSqlAddressGroup addressGroup =
        VertxSqlAddressGroup.of(
            asList(
                new SqlConnectOptions().setHost("db-b.example").setPort(5432),
                new SqlConnectOptions().setHost("2001:db8::1").setPort(15432)),
            "postgresql");

    assertThat(addressGroup.getAddress()).isEqualTo("db-b.example:5432,[2001:db8::1]:15432");
    assertThat(addressGroup.getPort()).isNull();
  }

  @Test
  void retainsPortsWhenTheDefaultIsUnknown() {
    VertxSqlAddressGroup addressGroup =
        VertxSqlAddressGroup.of(
            asList(
                new SqlConnectOptions().setHost("192.0.2.1").setPort(1234),
                new SqlConnectOptions().setHost("2001:db8::1").setPort(1234)),
            "other_sql");

    assertThat(addressGroup.getAddress()).isEqualTo("192.0.2.1:1234,[2001:db8::1]:1234");
    assertThat(addressGroup.getPort()).isNull();
  }

  @Test
  void retainsSinglePortWhenTheDefaultIsUnknown() {
    VertxSqlAddressGroup addressGroup =
        VertxSqlAddressGroup.of(
            new SqlConnectOptions().setHost("discovery.example").setPort(1234), "other_sql");

    assertThat(addressGroup.getAddress()).isEqualTo("discovery.example:1234");
    assertThat(addressGroup.getPort()).isNull();
  }

  @Test
  void preservesSingleUnixDomainSocketWithoutPort() {
    VertxSqlAddressGroup addressGroup =
        VertxSqlAddressGroup.of(
            new SqlConnectOptions().setHost("/var/run/postgres:primary").setPort(5432));

    assertThat(addressGroup.getAddress()).isEqualTo("/var/run/postgres:primary");
    assertThat(addressGroup.getPort()).isNull();
  }

  @Test
  void omitsAddressAndPortForAddressGroupContainingUnixDomainSocket() {
    VertxSqlAddressGroup addressGroup =
        VertxSqlAddressGroup.of(
            asList(
                new SqlConnectOptions().setHost("/var/run/postgres:primary").setPort(5433),
                new SqlConnectOptions().setHost("2001:db8::1").setPort(5432)),
            "postgresql");

    assertThat(addressGroup.getAddress()).isNull();
    assertThat(addressGroup.getPort()).isNull();
  }

  @Test
  void canNormalizeAStoredSnapshotOnceTheDriverIsKnown() {
    SqlConnectOptions options = new SqlConnectOptions().setHost("db.example").setPort(5432);
    VertxSqlAddressGroup addressGroup = VertxSqlAddressGroup.of(options);
    options.setHost("mutated.example").setPort(15432);

    VertxSqlAddressGroup normalized = addressGroup.withDbSystem("postgresql");

    assertThat(normalized.getAddress()).isEqualTo("db.example");
    assertThat(normalized.getPort()).isNull();
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
    assertThat(
            VertxSqlAddressGroup.of(
                new SqlConnectOptions().setHost(host).setPort(5432), "postgresql"))
        .isNull();
  }
}
