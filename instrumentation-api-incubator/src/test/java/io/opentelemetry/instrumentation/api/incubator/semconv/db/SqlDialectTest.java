/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SqlDialectTest {

  @ParameterizedTest
  @MethodSource("doubleQuotedQueries")
  void sanitizesDoubleQuotedQueries(String dbSystemName, boolean identifiers) {
    assertThat(
            SqlQueryAnalyzer.create(true)
                .analyze(
                    "SELECT \"name\" FROM catalog WHERE id = 42",
                    SqlDialect.forDbSystemName(dbSystemName))
                .getQueryText())
        .isEqualTo(
            identifiers
                ? "SELECT \"name\" FROM catalog WHERE id = ?"
                : "SELECT ? FROM catalog WHERE id = ?");
  }

  private static Stream<Arguments> doubleQuotedQueries() {
    return Stream.of(
        argumentSet("PostgreSQL", "postgresql", true),
        argumentSet("Oracle", "oracle.db", true),
        argumentSet("DB2", "ibm.db2", true),
        argumentSet("Derby", "derby", true),
        argumentSet("HSQLDB", "hsqldb", true),
        argumentSet("SAP HANA", "sap.hana", true),
        argumentSet("ClickHouse", "clickhouse", true),
        argumentSet("PolarDB", "polardb", true),
        argumentSet("Cassandra", "cassandra", true),
        argumentSet("Geode", "geode", true),
        argumentSet("InfluxDB", "influxdb", true),
        argumentSet("MySQL", "mysql", false),
        argumentSet("MariaDB", "mariadb", false),
        argumentSet("SQL Server", "microsoft.sql_server", false),
        argumentSet("Couchbase", "couchbase", false),
        argumentSet("unknown", "other_sql", false),
        argumentSet("null", null, false));
  }
}
