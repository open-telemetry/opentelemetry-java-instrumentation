/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SqlDialectTest {

  @Test
  void builderDefaultsToSanitizingDoubleQuotedFragments() {
    assertThat(
            SqlQueryAnalyzer.create(true)
                .analyze("SELECT \"name\" FROM catalog", SqlDialect.builder().build())
                .getQueryText())
        .isEqualTo("SELECT ? FROM catalog");
  }

  @ParameterizedTest
  @MethodSource("databaseNamesAndExpectedSanitizedQueries")
  void selectsQuotingPolicyForDatabase(String dbSystemName, String expectedQueryText) {
    assertThat(
            SqlQueryAnalyzer.create(true)
                .analyze("SELECT \"name\" FROM catalog", SqlDialect.forDbSystemName(dbSystemName))
                .getQueryText())
        .isEqualTo(expectedQueryText);
  }

  private static Stream<Arguments> databaseNamesAndExpectedSanitizedQueries() {
    return Stream.of(
        argumentSet("PostgreSQL", "postgresql", "SELECT \"name\" FROM catalog"),
        argumentSet("Oracle", "oracle.db", "SELECT \"name\" FROM catalog"),
        argumentSet("DB2", "ibm.db2", "SELECT \"name\" FROM catalog"),
        argumentSet("Derby", "derby", "SELECT \"name\" FROM catalog"),
        argumentSet("HSQLDB", "hsqldb", "SELECT \"name\" FROM catalog"),
        argumentSet("SAP HANA", "sap.hana", "SELECT \"name\" FROM catalog"),
        argumentSet("ClickHouse", "clickhouse", "SELECT \"name\" FROM catalog"),
        argumentSet("PolarDB", "polardb", "SELECT \"name\" FROM catalog"),
        argumentSet("Cassandra", "cassandra", "SELECT \"name\" FROM catalog"),
        argumentSet("Geode", "geode", "SELECT ? FROM catalog"),
        argumentSet("InfluxDB", "influxdb", "SELECT \"name\" FROM catalog"),
        argumentSet("MySQL", "mysql", "SELECT ? FROM catalog"),
        argumentSet("MariaDB", "mariadb", "SELECT ? FROM catalog"),
        argumentSet("SQL Server", "microsoft.sql_server", "SELECT ? FROM catalog"),
        argumentSet("Couchbase", "couchbase", "SELECT ? FROM catalog"),
        argumentSet("unknown", "other_sql", "SELECT ? FROM catalog"),
        argumentSet("null", null, "SELECT ? FROM catalog"));
  }
}
