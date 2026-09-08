/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.ClearSystemProperty;

@ClearSystemProperty(key = "otel.instrumentation.jdbc.query-sanitization.enabled")
@ClearSystemProperty(key = "otel.instrumentation.common.db.query-sanitization.enabled")
@ClearSystemProperty(key = "otel.instrumentation.common.db-statement-sanitizer.enabled")
class OpenTelemetryDriverQuerySanitizationTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @BeforeEach
  void registerDriver() {
    Driver driver = new org.h2.Driver();
    OpenTelemetryDriver.addDriverCandidate(driver);
    cleanup.deferCleanup(() -> OpenTelemetryDriver.removeDriverCandidate(driver));
  }

  @ParameterizedTest
  @MethodSource("sanitizationSettings")
  void systemProperties(Boolean jdbcEnabled, Boolean commonEnabled, boolean expected)
      throws SQLException {
    if (jdbcEnabled != null) {
      System.setProperty(
          "otel.instrumentation.jdbc.query-sanitization.enabled", jdbcEnabled.toString());
    }
    if (commonEnabled != null) {
      System.setProperty(
          "otel.instrumentation.common.db.query-sanitization.enabled", commonEnabled.toString());
    }

    assertQuerySanitization(otelTesting.getOpenTelemetry(), expected);
  }

  @ParameterizedTest
  @MethodSource("sanitizationSettings")
  void declarativeConfiguration(Boolean jdbcEnabled, Boolean commonEnabled, boolean expected)
      throws SQLException {
    assertQuerySanitization(configuredOpenTelemetry(jdbcEnabled, commonEnabled), expected);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void declarativeCommonTakesPrecedenceOverSystemFallbacks(boolean enabled) throws SQLException {
    System.setProperty(
        "otel.instrumentation.jdbc.query-sanitization.enabled", Boolean.toString(!enabled));
    System.setProperty(
        "otel.instrumentation.common.db.query-sanitization.enabled", Boolean.toString(!enabled));

    assertQuerySanitization(configuredOpenTelemetry(null, enabled), enabled);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void deprecatedJdbcSettingUsesInstanceV3Preview(boolean v3Preview) throws SQLException {
    ExtendedOpenTelemetry openTelemetry = configuredOpenTelemetry(null, null);
    when(openTelemetry.getInstrumentationConfig("common").getBoolean("v3_preview"))
        .thenReturn(v3Preview);
    when(openTelemetry
            .getInstrumentationConfig("jdbc")
            .get("statement_sanitizer")
            .getBoolean("enabled"))
        .thenReturn(false);

    assertQuerySanitization(openTelemetry, v3Preview);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void deprecatedSystemFallbackUsesInstanceV3Preview(boolean v3Preview) throws SQLException {
    ExtendedOpenTelemetry openTelemetry = configuredOpenTelemetry(null, null);
    when(openTelemetry.getInstrumentationConfig("common").getBoolean("v3_preview"))
        .thenReturn(v3Preview);
    System.setProperty("otel.instrumentation.common.db-statement-sanitizer.enabled", "false");

    assertQuerySanitization(openTelemetry, v3Preview);
  }

  private static ExtendedOpenTelemetry configuredOpenTelemetry(
      Boolean jdbcEnabled, Boolean commonEnabled) {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    when(openTelemetry.getTracerProvider())
        .thenReturn(otelTesting.getOpenTelemetry().getTracerProvider());
    when(openTelemetry.getMeterProvider())
        .thenReturn(otelTesting.getOpenTelemetry().getMeterProvider());
    when(openTelemetry.getLogsBridge()).thenReturn(otelTesting.getOpenTelemetry().getLogsBridge());
    when(openTelemetry.getPropagators())
        .thenReturn(otelTesting.getOpenTelemetry().getPropagators());
    when(openTelemetry.getGeneralInstrumentationConfig())
        .thenReturn(DeclarativeConfigProperties.empty());
    DeclarativeConfigProperties jdbcConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    ConfigProvider configProvider = mock(ConfigProvider.class);
    when(openTelemetry.getConfigProvider()).thenReturn(configProvider);
    when(configProvider.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(openTelemetry.getInstrumentationConfig("jdbc")).thenReturn(jdbcConfig);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(jdbcConfig.get("query_sanitization").getBoolean("enabled")).thenReturn(jdbcEnabled);
    when(jdbcConfig.get("statement_sanitizer").getBoolean("enabled")).thenReturn(null);
    when(commonConfig.get("db").get("query_sanitization").getBoolean("enabled"))
        .thenReturn(commonEnabled);
    when(commonConfig.get("database").get("statement_sanitizer").getBoolean("enabled"))
        .thenReturn(null);
    when(commonConfig.get("db_statement_sanitizer").getBoolean("enabled")).thenReturn(null);

    return openTelemetry;
  }

  private static Stream<Arguments> sanitizationSettings() {
    return Stream.of(
        argumentSet("enabled by default", null, null, true),
        argumentSet("JDBC disables sanitization", false, null, false),
        argumentSet("common disables sanitization", null, false, false),
        argumentSet("JDBC enables over common", true, false, true),
        argumentSet("JDBC disables over common", false, true, false));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static void assertQuerySanitization(OpenTelemetry openTelemetry, boolean expected)
      throws SQLException {
    OpenTelemetryDriver driver = new OpenTelemetryDriver();
    driver.setOpenTelemetry(openTelemetry);
    Connection connection = driver.connect("jdbc:otel:h2:mem:", new Properties());
    cleanup.deferCleanup(connection);
    Statement statement = connection.createStatement();
    cleanup.deferCleanup(statement);
    ResultSet resultSet = statement.executeQuery("SELECT 'test-value'");
    cleanup.deferCleanup(resultSet);

    assertThat(resultSet.next()).isTrue();
    assertThat(resultSet.getString(1)).isEqualTo("test-value");
    assertThat(otelTesting.getSpans())
        .singleElement()
        .satisfies(
            span ->
                assertThat(span)
                    .hasAttribute(
                        equalTo(
                            maybeStable(DB_STATEMENT),
                            expected ? "SELECT ?" : "SELECT 'test-value'")));
  }
}
