/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DbSystemNameUtilTest {

  @ParameterizedTest
  @MethodSource("aliases")
  void normalizesAliases(String dbSystemName, String expectedDbSystemName) {
    assertThat(DbSystemNameUtil.normalizeDbSystemName(dbSystemName))
        .isEqualTo(expectedDbSystemName);
  }

  private static Stream<Arguments> aliases() {
    return Stream.of(
        argumentSet("Adabas", "adabas", "softwareag.adabas"),
        argumentSet("InterSystems Cache", "intersystems_cache", "intersystems.cache"),
        argumentSet("Cosmos DB", "cosmosdb", "azure.cosmosdb"),
        argumentSet("DB2", "db2", "ibm.db2"),
        argumentSet("DynamoDB", "dynamodb", "aws.dynamodb"),
        argumentSet("H2", "h2", "h2database"),
        argumentSet("SAP HANA", "hanadb", "sap.hana"),
        argumentSet("Informix", "informix", "ibm.informix"),
        argumentSet("Ingres", "ingres", "actian.ingres"),
        argumentSet("MaxDB", "maxdb", "sap.maxdb"),
        argumentSet("SQL Server", "mssql", "microsoft.sql_server"),
        argumentSet("Netezza", "netezza", "ibm.netezza"),
        argumentSet("Oracle", "oracle", "oracle.db"),
        argumentSet("Redshift", "redshift", "aws.redshift"),
        argumentSet("Spanner", "spanner", "gcp.spanner"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "softwareag.adabas",
        "intersystems.cache",
        "azure.cosmosdb",
        "ibm.db2",
        "aws.dynamodb",
        "h2database",
        "sap.hana",
        "ibm.informix",
        "actian.ingres",
        "sap.maxdb",
        "microsoft.sql_server",
        "ibm.netezza",
        "oracle.db",
        "aws.redshift",
        "gcp.spanner",
        "postgresql",
        "mysql",
        "redis",
        "custom",
        "DB2",
        ""
      })
  void preservesUnmappedNames(String dbSystemName) {
    assertThat(DbSystemNameUtil.normalizeDbSystemName(dbSystemName)).isEqualTo(dbSystemName);
  }
}
