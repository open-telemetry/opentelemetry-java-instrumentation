/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.test;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStableDbSystemName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_QUERY_PARAMETER;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.hsqldb.jdbc.JDBCDriver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class PreparedStatementParametersTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final String dbName = "jdbcUnitTest";
  private static final String dbNameLower = dbName.toLowerCase(Locale.ROOT);

  private static final Map<String, String> jdbcUrls =
      ImmutableMap.of(
          "h2", "jdbc:h2:mem:" + dbName,
          "derby", "jdbc:derby:memory:" + dbName,
          "hsqldb", "jdbc:hsqldb:mem:" + dbName);
  private static final Map<String, String> jdbcUserNames = Maps.newHashMap();
  private static final Properties connectionProps = new Properties();

  static {
    jdbcUserNames.put("derby", "APP");
    jdbcUserNames.put("h2", null);
    jdbcUserNames.put("hsqldb", "SA");

    connectionProps.put("databaseName", "someDb");
    connectionProps.put("OPEN_NEW", "true"); // So H2 doesn't complain about username/password.
    connectionProps.put("create", "true");
  }

  static Stream<Arguments> preparedStatementStream() throws SQLException {
    return Stream.of(
        Arguments.of(
            "h2",
            new org.h2.Driver().connect(jdbcUrls.get("h2"), null),
            null,
            "SELECT 3, ?",
            "SELECT 3, ?",
            "SELECT " + dbNameLower,
            "h2:mem:",
            null),
        Arguments.of(
            "derby",
            new EmbeddedDriver().connect(jdbcUrls.get("derby"), connectionProps),
            "APP",
            "SELECT 3 FROM SYSIBM.SYSDUMMY1 WHERE IBMREQD=? OR 1=1",
            "SELECT 3 FROM SYSIBM.SYSDUMMY1 WHERE IBMREQD=? OR 1=1",
            "SELECT SYSIBM.SYSDUMMY1",
            "derby:memory:",
            "SYSIBM.SYSDUMMY1"),
        Arguments.of(
            "hsqldb",
            new JDBCDriver().connect(jdbcUrls.get("hsqldb"), null),
            "SA",
            "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS WHERE USER_NAME=? OR 1=1",
            "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS WHERE USER_NAME=? OR 1=1",
            "SELECT INFORMATION_SCHEMA.SYSTEM_USERS",
            "hsqldb:mem:",
            "INFORMATION_SCHEMA.SYSTEM_USERS"));
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testBooleanPreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    test(
        system,
        connection,
        username,
        query,
        sanitizedQuery,
        spanName,
        url,
        table,
        statement -> statement.setBoolean(1, true),
        "true");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testShortPreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    test(
        system,
        connection,
        username,
        query,
        sanitizedQuery,
        spanName,
        url,
        table,
        statement -> statement.setShort(1, (short) 0),
        "0");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testIntPreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    test(
        system,
        connection,
        username,
        query,
        sanitizedQuery,
        spanName,
        url,
        table,
        statement -> statement.setInt(1, 0),
        "0");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testLongPreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    test(
        system,
        connection,
        username,
        query,
        sanitizedQuery,
        spanName,
        url,
        table,
        statement -> statement.setLong(1, 0),
        "0");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testFloatPreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    test(
        system,
        connection,
        username,
        query,
        sanitizedQuery,
        spanName,
        url,
        table,
        statement -> statement.setFloat(1, 0.1f),
        "0.1");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testDoublePreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    test(
        system,
        connection,
        username,
        query,
        sanitizedQuery,
        spanName,
        url,
        table,
        statement -> statement.setDouble(1, 0.1),
        "0.1");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testBigDecimalPreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    test(
        system,
        connection,
        username,
        query,
        sanitizedQuery,
        spanName,
        url,
        table,
        statement -> statement.setBigDecimal(1, BigDecimal.ZERO),
        "0");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testStringPreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    test(
        system,
        connection,
        username,
        query,
        sanitizedQuery,
        spanName,
        url,
        table,
        statement -> statement.setString(1, "S"),
        "S");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testObjectPreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    test(
        system,
        connection,
        username,
        query,
        sanitizedQuery,
        spanName,
        url,
        table,
        statement -> statement.setObject(1, "S"),
        "S");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testObjectWithTypePreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    // we are using old database drivers that don't support the tested setObject method
    Assumptions.assumeTrue(Boolean.getBoolean("testLatestDeps"));

    test(
        system,
        connection,
        username,
        query,
        sanitizedQuery,
        spanName,
        url,
        table,
        statement -> statement.setObject(1, "S", Types.CHAR),
        "S");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testDate2PreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    String updatedQuery = query.replace("USER_NAME=?", "CURDATE()=?");
    String updatedQuerySanitized = sanitizedQuery.replace("USER_NAME=?", "CURDATE()=?");

    test(
        system,
        connection,
        username,
        updatedQuery,
        updatedQuerySanitized,
        spanName,
        url,
        table,
        statement -> statement.setDate(1, Date.valueOf("2000-01-01")),
        "2000-01-01");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testDate3PreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    String updatedQuery = query.replace("USER_NAME=?", "CURDATE()=?");
    String updatedQuerySanitized = sanitizedQuery.replace("USER_NAME=?", "CURDATE()=?");

    test(
        system,
        connection,
        username,
        updatedQuery,
        updatedQuerySanitized,
        spanName,
        url,
        table,
        statement -> statement.setDate(1, Date.valueOf("2000-01-01"), Calendar.getInstance()),
        "2000-01-01");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testTime2PreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    String updatedQuery = query.replace("USER_NAME=?", "CURTIME()=?");
    String updatedQuerySanitized = sanitizedQuery.replace("USER_NAME=?", "CURTIME()=?");

    test(
        system,
        connection,
        username,
        updatedQuery,
        updatedQuerySanitized,
        spanName,
        url,
        table,
        statement -> statement.setTime(1, Time.valueOf("00:00:00")),
        "00:00:00");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testTime3PreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    String updatedQuery = query.replace("USER_NAME=?", "CURTIME()=?");
    String updatedQuerySanitized = sanitizedQuery.replace("USER_NAME=?", "CURTIME()=?");

    test(
        system,
        connection,
        username,
        updatedQuery,
        updatedQuerySanitized,
        spanName,
        url,
        table,
        statement -> statement.setTime(1, Time.valueOf("00:00:00"), Calendar.getInstance()),
        "00:00:00");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testTimestamp2PreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    String updatedQuery = query.replace("USER_NAME=?", "NOW()=?");
    String updatedQuerySanitized = sanitizedQuery.replace("USER_NAME=?", "NOW()=?");

    test(
        system,
        connection,
        username,
        updatedQuery,
        updatedQuerySanitized,
        spanName,
        url,
        table,
        statement -> statement.setTimestamp(1, Timestamp.valueOf("2000-01-01 00:00:00")),
        "2000-01-01 00:00:00.0");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testTimestamp3PreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    String updatedQuery = query.replace("USER_NAME=?", "NOW()=?");
    String updatedQuerySanitized = sanitizedQuery.replace("USER_NAME=?", "NOW()=?");

    test(
        system,
        connection,
        username,
        updatedQuery,
        updatedQuerySanitized,
        spanName,
        url,
        table,
        statement ->
            statement.setTimestamp(
                1, Timestamp.valueOf("2000-01-01 00:00:00"), Calendar.getInstance()),
        "2000-01-01 00:00:00.0");
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testNstringPreparedStatementParameter(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    Assumptions.assumeFalse(system.equalsIgnoreCase("derby"));

    test(
        system,
        connection,
        username,
        query,
        sanitizedQuery,
        spanName,
        url,
        table,
        statement -> statement.setNString(1, "S"),
        "S");
  }

  private static void test(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table,
      ThrowingConsumer<PreparedStatement, SQLException> setParameter,
      String expectedParameterValue)
      throws SQLException {
    PreparedStatement statement = connection.prepareStatement(query);
    cleanup.deferCleanup(statement);

    ResultSet resultSet =
        testing.runWithSpan(
            "parent",
            () -> {
              setParameter.accept(statement);
              statement.execute();
              return statement.getResultSet();
            });

    resultSet.next();
    assertThat(resultSet.getInt(1)).isEqualTo(3);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(spanName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                            equalTo(maybeStable(DB_NAME), dbNameLower),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : username),
                            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : url),
                            equalTo(maybeStable(DB_STATEMENT), sanitizedQuery),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), table),
                            equalTo(
                                DB_QUERY_PARAMETER.getAttributeKey("0"), expectedParameterValue))));
  }

  public interface ThrowingConsumer<T, E extends Throwable> {
    void accept(T t) throws E;
  }
}
