/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JdbcConnectionPoolMetricsUtilTest {

  private static final String FALLBACK_NAME = "fallback";

  @Test
  void returnsExpectedPoolNameFromProperties() {
    Properties properties = new Properties();
    properties.setProperty("serverName", "properties.example");
    properties.put("portNumber", 5433);
    properties.setProperty("databaseName", "inventory");

    DbInfo dbInfo = JdbcConnectionPoolMetricsUtil.dbInfo(properties);

    assertThat(JdbcConnectionPoolMetricsUtil.poolName(dbInfo, null, FALLBACK_NAME))
        .isEqualTo(emitStableDatabaseSemconv() ? "inventory" : "properties.example:5433/inventory");
  }

  @Test
  void returnsExpectedPoolNameFromDefaultProperties() {
    Properties defaults = new Properties();
    defaults.setProperty("serverName", "properties.example");
    defaults.setProperty("portNumber", "5433");
    defaults.setProperty("databaseName", "inventory");
    Properties properties = new Properties(defaults);

    DbInfo dbInfo = JdbcConnectionPoolMetricsUtil.dbInfo(properties);

    assertThat(JdbcConnectionPoolMetricsUtil.poolName(dbInfo, null, FALLBACK_NAME))
        .isEqualTo(emitStableDatabaseSemconv() ? "inventory" : "properties.example:5433/inventory");
  }

  @Test
  void returnsExpectedPoolNameFromPropertiesWithBracketedIpv6Address() {
    Properties properties = new Properties();
    properties.setProperty("serverName", "[2001:db8::1]");
    properties.setProperty("portNumber", "5432");
    properties.setProperty("databaseName", "orders");

    DbInfo dbInfo = JdbcConnectionPoolMetricsUtil.dbInfo(properties);

    assertThat(JdbcConnectionPoolMetricsUtil.poolName(dbInfo, null, FALLBACK_NAME))
        .isEqualTo(emitStableDatabaseSemconv() ? "orders" : "[2001:db8::1]:5432/orders");
  }

  @Test
  void returnsFallbackNameFromPropertiesWithoutUsableValues() {
    Properties properties = new Properties();
    properties.setProperty("serverName", "");
    properties.setProperty("portNumber", "invalid");
    properties.setProperty("databaseName", "");

    DbInfo dbInfo = JdbcConnectionPoolMetricsUtil.dbInfo(properties);

    assertThat(JdbcConnectionPoolMetricsUtil.poolName(dbInfo, null, FALLBACK_NAME))
        .isEqualTo(FALLBACK_NAME);
  }

  @ParameterizedTest
  @MethodSource("propertyArguments")
  void parsesLegacyEndpointAndConfiguredTargetFromProperties(
      String serverName,
      String portNumber,
      String expectedLegacyAddress,
      Integer expectedLegacyPort,
      DbServerTarget expectedTarget) {
    Properties properties = new Properties();
    if (serverName != null) {
      properties.setProperty("serverName", serverName);
    }
    if (portNumber != null) {
      properties.setProperty("portNumber", portNumber);
    }

    DbInfo dbInfo = JdbcConnectionPoolMetricsUtil.dbInfo(properties);

    assertThat(dbInfo.getLegacyServerAddress()).isEqualTo(expectedLegacyAddress);
    assertThat(dbInfo.getLegacyServerPort()).isEqualTo(expectedLegacyPort);
    assertThat(dbInfo.getConfiguredServerTarget()).isEqualTo(expectedTarget);
  }

  private static Stream<Arguments> propertyArguments() {
    return Stream.of(
        argumentSet(
            "address and port",
            "db.example",
            "5432",
            "db.example",
            5432,
            DbServerTarget.create("db.example", 5432)),
        argumentSet(
            "portless address",
            "db.example",
            null,
            "db.example",
            null,
            DbServerTarget.create("db.example", null)),
        argumentSet(
            "invalid port",
            "db.example",
            "invalid",
            "db.example",
            null,
            DbServerTarget.create("db.example", null)),
        argumentSet(
            "bracketed IPv6 address",
            "[2001:db8::1]",
            "5432",
            "2001:db8::1",
            5432,
            DbServerTarget.create("2001:db8::1", 5432)),
        argumentSet("missing address", null, "5432", null, 5432, null),
        argumentSet("empty address", "", "5432", null, 5432, null),
        argumentSet("empty bracketed address", "[]", "5432", "", 5432, null),
        argumentSet("no endpoint", null, null, null, null, null));
  }

  @ParameterizedTest
  @MethodSource("databaseAttributesArguments")
  void returnsConfiguredDatabaseAttributes(DbServerTarget target, Attributes expectedAttributes) {
    DbInfo dbInfo =
        DbInfo.builder()
            .dbSystemName("postgresql")
            .dbNamespace("orders")
            .legacyServerAddress("legacy.example")
            .legacyServerPort(15432)
            .configuredServerTarget(target)
            .build();

    assertThat(JdbcConnectionPoolMetricsUtil.databaseAttributes(dbInfo))
        .isEqualTo(expectedAttributes);
  }

  private static Stream<Arguments> databaseAttributesArguments() {
    return Stream.of(
        argumentSet(
            "configured address and port",
            DbServerTarget.create("db.example", 5432),
            Attributes.of(
                DB_SYSTEM_NAME, "postgresql",
                DB_NAMESPACE, "orders",
                SERVER_ADDRESS, "db.example",
                SERVER_PORT, 5432L)),
        argumentSet(
            "portless configured address",
            DbServerTarget.create("db.example", null),
            Attributes.of(
                DB_SYSTEM_NAME, "postgresql",
                DB_NAMESPACE, "orders",
                SERVER_ADDRESS, "db.example")),
        argumentSet(
            "configured target list",
            DbServerTarget.create("db-a:5432,db-b:6432", null),
            Attributes.of(
                DB_SYSTEM_NAME, "postgresql",
                DB_NAMESPACE, "orders",
                SERVER_ADDRESS, "db-a:5432,db-b:6432")),
        argumentSet(
            "no configured target",
            null,
            Attributes.of(DB_SYSTEM_NAME, "postgresql", DB_NAMESPACE, "orders")));
  }

  @Test
  void explicitPoolNameTakesPrecedenceAndPreservesDatabaseAttributes() {
    DbInfo dbInfo =
        DbInfo.builder()
            .dbSystemName("postgresql")
            .dbNamespace("orders")
            .configuredServerTarget(DbServerTarget.create("db.example", 5432))
            .build();

    assertThat(JdbcConnectionPoolMetricsUtil.poolName(dbInfo, "explicit", FALLBACK_NAME))
        .isEqualTo("explicit");
    assertThat(JdbcConnectionPoolMetricsUtil.databaseAttributes(dbInfo))
        .isEqualTo(
            Attributes.of(
                DB_SYSTEM_NAME, "postgresql",
                DB_NAMESPACE, "orders",
                SERVER_ADDRESS, "db.example",
                SERVER_PORT, 5432L));
  }

  @ParameterizedTest
  @MethodSource("poolNameArguments")
  void returnsExpectedPoolName(
      DbInfo dbInfo, String oldExpectedPoolName, String stableExpectedPoolName) {
    assertThat(JdbcConnectionPoolMetricsUtil.poolName(dbInfo, null, FALLBACK_NAME))
        .isEqualTo(emitStableDatabaseSemconv() ? stableExpectedPoolName : oldExpectedPoolName);
  }

  private static Stream<Arguments> poolNameArguments() {
    return Stream.of(
        argumentSet(
            "address, port, and namespace",
            DbInfo.builder()
                .legacyServerAddress("db.example")
                .legacyServerPort(5432)
                .dbNamespace("orders")
                .build(),
            "db.example:5432/orders",
            "orders"),
        argumentSet(
            "IPv6 address, port, and namespace",
            DbInfo.builder()
                .legacyServerAddress("2001:db8::1")
                .legacyServerPort(5432)
                .dbNamespace("orders")
                .build(),
            "[2001:db8::1]:5432/orders",
            "orders"),
        argumentSet(
            "address only",
            DbInfo.builder().legacyServerAddress("db.example").build(),
            "db.example",
            FALLBACK_NAME),
        argumentSet(
            "address and port",
            DbInfo.builder().legacyServerAddress("db.example").legacyServerPort(5432).build(),
            "db.example:5432",
            FALLBACK_NAME),
        argumentSet(
            "address and namespace",
            DbInfo.builder().legacyServerAddress("db.example").dbNamespace("orders").build(),
            "db.example/orders",
            "orders"),
        argumentSet(
            "namespace only", DbInfo.builder().dbNamespace("orders").build(), "orders", "orders"),
        argumentSet(
            "port only",
            DbInfo.builder().legacyServerPort(5432).build(),
            FALLBACK_NAME,
            FALLBACK_NAME),
        argumentSet(
            "port and namespace",
            DbInfo.builder().legacyServerPort(5432).dbNamespace("orders").build(),
            "orders",
            "orders"),
        argumentSet(
            "configured address and port",
            DbInfo.builder()
                .configuredServerTarget(DbServerTarget.create("db.example", 5432))
                .build(),
            FALLBACK_NAME,
            "db.example:5432"),
        argumentSet(
            "configured IPv6 address and port",
            DbInfo.builder()
                .configuredServerTarget(DbServerTarget.create("2001:db8::1", 5432))
                .build(),
            FALLBACK_NAME,
            "[2001:db8::1]:5432"),
        argumentSet(
            "configured target list",
            DbInfo.builder()
                .configuredServerTarget(DbServerTarget.create("db-a:5432,db-b:6432", null))
                .build(),
            FALLBACK_NAME,
            "db-a:5432,db-b:6432"),
        argumentSet(
            "portless configured IPv6 address",
            DbInfo.builder()
                .configuredServerTarget(DbServerTarget.create("2001:db8::1", null))
                .build(),
            FALLBACK_NAME,
            "2001:db8::1"),
        argumentSet(
            "empty namespace",
            DbInfo.builder()
                .legacyServerAddress("db.example")
                .dbNamespace("")
                .configuredServerTarget(DbServerTarget.create("db.example", null))
                .build(),
            "db.example/",
            "db.example"),
        argumentSet(
            "empty configured address",
            DbInfo.builder()
                .dbSystemName("postgresql")
                .legacyServerAddress("postgresql")
                .configuredServerTarget(DbServerTarget.create("", null))
                .build(),
            "postgresql",
            "postgresql"),
        argumentSet(
            "empty database system",
            DbInfo.builder().dbSystemName("").build(),
            FALLBACK_NAME,
            FALLBACK_NAME),
        argumentSet(
            "no address, port, or namespace", DbInfo.DEFAULT, FALLBACK_NAME, FALLBACK_NAME));
  }
}
