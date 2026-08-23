/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.DbExecution;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.R2dbcSqlAttributesGetter;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.proxy.test.MockConnectionInfo;
import io.r2dbc.proxy.test.MockQueryExecutionInfo;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.util.Collection;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // testing old database semantic conventions
class R2dbcSqlAttributesGetterTest {

  private final R2dbcSqlAttributesGetter getter = new R2dbcSqlAttributesGetter();

  @Test
  void rawQueryTextsForSingleQuery() {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("INSERT INTO person VALUES(1)"))
            .connectionInfo(MockConnectionInfo.builder().build())
            .build();
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.parse("r2dbc:postgresql://localhost/db");
    DbExecution dbExecution = new DbExecution(queryExecutionInfo, factoryOptions);

    Collection<String> rawQueryTexts = getter.getRawQueryTexts(dbExecution);

    assertThat(rawQueryTexts).isSameAs(dbExecution.getRawQueryTexts());
    assertThat(rawQueryTexts).containsExactly("INSERT INTO person VALUES(1)");
    assertThat(getter.getRawQueryTextsForOldSemconv(dbExecution)).isSameAs(rawQueryTexts);
  }

  @Test
  void rawQueryTextsForBatch() {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("INSERT INTO person VALUES(1)"))
            .queryInfo(new QueryInfo("INSERT INTO person VALUES(2)"))
            .batchSize(2)
            .connectionInfo(MockConnectionInfo.builder().build())
            .build();
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.parse("r2dbc:postgresql://localhost/db");
    DbExecution dbExecution = new DbExecution(queryExecutionInfo, factoryOptions);

    Collection<String> rawQueryTexts = getter.getRawQueryTexts(dbExecution);

    assertThat(rawQueryTexts).isSameAs(dbExecution.getRawQueryTexts());
    assertThat(rawQueryTexts)
        .containsExactly("INSERT INTO person VALUES(1)", "INSERT INTO person VALUES(2)");
    assertThat(getter.getRawQueryTextsForOldSemconv(dbExecution))
        .containsExactly("INSERT INTO person VALUES(1);\nINSERT INTO person VALUES(2)");
    assertThat(getter.getDbOperationBatchSize(dbExecution)).isEqualTo(2);
  }

  @ParameterizedTest
  @MethodSource("dialects")
  void sqlDialect(String connectionFactoryUrl, SqlDialect expectedDialect) {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("SELECT * FROM \"customers\""))
            .connectionInfo(MockConnectionInfo.builder().build())
            .build();
    DbExecution dbExecution =
        new DbExecution(queryExecutionInfo, ConnectionFactoryOptions.parse(connectionFactoryUrl));

    assertThat(getter.getSqlDialect(dbExecution)).isEqualTo(expectedDialect);
  }

  private static Stream<Arguments> dialects() {
    return Stream.of(
        argumentSet("PostgreSQL", "r2dbc:postgresql://localhost/db", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("Oracle", "r2dbc:oracle://localhost/db", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("DB2", "r2dbc:db2://localhost/db", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("ClickHouse", "r2dbc:clickhouse://localhost/db", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("MySQL", "r2dbc:mysql://localhost/db", DOUBLE_QUOTES_ARE_STRING_LITERALS),
        argumentSet("SQL Server", "r2dbc:mssql://localhost/db", DOUBLE_QUOTES_ARE_STRING_LITERALS),
        argumentSet("unknown", "r2dbc:unknown://localhost/db", DOUBLE_QUOTES_ARE_STRING_LITERALS));
  }
}
