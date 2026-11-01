/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SqlQueryTest {

  @ParameterizedTest
  @MethodSource("operationAndTarget")
  void threeArgumentFactoryPreservesOperationAndTarget(
      String operation, String target, String collection, String storedProcedure) {
    SqlQuery query = SqlQuery.create("query text", operation, target);

    assertThat(query.getQueryText()).isEqualTo("query text");
    assertThat(query.getOperationName()).isEqualTo(operation);
    assertThat(query.getCollectionName()).isEqualTo(collection);
    assertThat(query.getStoredProcedureName()).isEqualTo(storedProcedure);
    assertThat(query.getQuerySummary()).isNull();
  }

  private static Stream<Arguments> operationAndTarget() {
    return Stream.of(
        Arguments.of("SELECT", "table", "table", null),
        Arguments.of("CALL", "procedure", null, "procedure"),
        Arguments.of("EXECUTE", "procedure", null, "procedure"),
        Arguments.of(null, "table", "table", null),
        Arguments.of("SELECT", null, null, null));
  }

  @Test
  void fullFactoryPreservesAnalysisValues() {
    SqlQuery query = SqlQuery.create("query text", "SELECT", "table", null, "SELECT table");

    assertThat(query.getQueryText()).isEqualTo("query text");
    assertThat(query.getOperationName()).isEqualTo("SELECT");
    assertThat(query.getCollectionName()).isEqualTo("table");
    assertThat(query.getStoredProcedureName()).isNull();
    assertThat(query.getQuerySummary()).isEqualTo("SELECT table");
  }

  @Test
  void summaryFactoryMigrationPreservesStoredProcedure() {
    SqlQuery query =
        SqlQuery.create("CALL procedure(?)", null, null, "procedure", "CALL procedure");

    assertThat(query.getQueryText()).isEqualTo("CALL procedure(?)");
    assertThat(query.getOperationName()).isNull();
    assertThat(query.getCollectionName()).isNull();
    assertThat(query.getStoredProcedureName()).isEqualTo("procedure");
    assertThat(query.getQuerySummary()).isEqualTo("CALL procedure");
  }

  @Test
  void factoriesAcceptNullValues() {
    SqlQuery query = SqlQuery.create(null, null, null, null, null);

    assertThat(SqlQuery.create(null, null, null)).isEqualTo(query);
    assertThat(query.getQueryText()).isNull();
    assertThat(query.getOperationName()).isNull();
    assertThat(query.getCollectionName()).isNull();
    assertThat(query.getStoredProcedureName()).isNull();
    assertThat(query.getQuerySummary()).isNull();
  }

  @Test
  void fullFactoryTruncatesOnlySummary() {
    String longIdentifier = new String(new char[256]).replace('\0', 'a');
    String queryText = "SELECT " + longIdentifier;
    SqlQuery query = SqlQuery.create(queryText, "SELECT", longIdentifier, null, queryText);

    assertThat(query.getQueryText()).isEqualTo(queryText);
    assertThat(query.getCollectionName()).isEqualTo(longIdentifier);
    assertThat(query.getQuerySummary()).isEqualTo("SELECT");
    assertThat(SqlQuery.create(null, null, null, null, longIdentifier).getQuerySummary())
        .isEqualTo(longIdentifier.substring(0, 255));
  }
}
