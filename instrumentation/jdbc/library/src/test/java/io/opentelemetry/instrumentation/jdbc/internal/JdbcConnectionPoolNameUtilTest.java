/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JdbcConnectionPoolNameUtilTest {

  private static final String FALLBACK_NAME = "fallback";

  @Test
  void returnsExpectedPoolNameFromProperties() {
    Properties properties = new Properties();
    properties.setProperty("serverName", "properties.example");
    properties.put("portNumber", 5433);
    properties.setProperty("databaseName", "inventory");

    assertThat(JdbcConnectionPoolNameUtil.poolName(properties, FALLBACK_NAME))
        .isEqualTo(emitStableDatabaseSemconv() ? "inventory" : "properties.example:5433/inventory");
  }

  @Test
  void returnsExpectedPoolNameFromDefaultProperties() {
    Properties defaults = new Properties();
    defaults.setProperty("serverName", "properties.example");
    defaults.setProperty("portNumber", "5433");
    defaults.setProperty("databaseName", "inventory");
    Properties properties = new Properties(defaults);

    assertThat(JdbcConnectionPoolNameUtil.poolName(properties, FALLBACK_NAME))
        .isEqualTo(emitStableDatabaseSemconv() ? "inventory" : "properties.example:5433/inventory");
  }

  @Test
  void returnsExpectedPoolNameFromPropertiesWithBracketedIpv6Address() {
    Properties properties = new Properties();
    properties.setProperty("serverName", "[2001:db8::1]");
    properties.setProperty("portNumber", "5432");
    properties.setProperty("databaseName", "orders");

    assertThat(JdbcConnectionPoolNameUtil.poolName(properties, FALLBACK_NAME))
        .isEqualTo(emitStableDatabaseSemconv() ? "orders" : "[2001:db8::1]:5432/orders");
  }

  @Test
  void returnsFallbackNameFromPropertiesWithoutUsableValues() {
    Properties properties = new Properties();
    properties.setProperty("serverName", "");
    properties.setProperty("portNumber", "invalid");
    properties.setProperty("databaseName", "");

    assertThat(JdbcConnectionPoolNameUtil.poolName(properties, FALLBACK_NAME))
        .isEqualTo(FALLBACK_NAME);
  }

  @ParameterizedTest
  @MethodSource("poolNameArguments")
  void returnsExpectedPoolName(
      DbInfo dbInfo, String oldExpectedPoolName, String stableExpectedPoolName) {
    assertThat(JdbcConnectionPoolNameUtil.poolName(dbInfo, FALLBACK_NAME))
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
