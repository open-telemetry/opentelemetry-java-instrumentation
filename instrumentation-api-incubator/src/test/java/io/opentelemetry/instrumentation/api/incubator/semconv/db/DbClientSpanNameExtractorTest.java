/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbClientSpanNameExtractorTest {
  @Mock DbClientAttributesGetter<DbRequest, Void> dbAttributesGetter;

  @Mock SqlClientAttributesGetter<DbRequest, Void> sqlAttributesGetter;

  @BeforeEach
  void setUp() {
    lenient().when(sqlAttributesGetter.getSqlDialect(any())).thenReturn(SqlDialect.DEFAULT);
  }

  @Test
  void shouldExtractFullSpanName() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(singleton("SELECT * from table"));
    when(sqlAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName)
        .isEqualTo(emitStableDatabaseSemconv() ? "SELECT table" : "SELECT database.table");
  }

  @Test
  void shouldSkipNamespaceIfTableAlreadyHasNamespacePrefix() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(singleton("SELECT * from another.table"));
    when(sqlAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("SELECT another.table");
  }

  @Test
  void shouldExtractOperationAndTable() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(singleton("SELECT * from table"));

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("SELECT table");
  }

  @Test
  void shouldExtractOperationAndName() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(dbAttributesGetter.getDbOperationName(dbRequest)).thenReturn("SELECT");
    when(dbAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(dbAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("SELECT database");
  }

  @Test
  void shouldExtractOperation() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(dbAttributesGetter.getDbOperationName(dbRequest)).thenReturn("SELECT");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(dbAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("SELECT");
  }

  @Test
  void shouldExtractNamespace() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(dbAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(dbAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("database");
  }

  @Test
  void shouldFallBackToDefaultSpanName() {
    // given
    DbRequest dbRequest = new DbRequest();

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(dbAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("DB Query");
  }

  @Test
  void shouldUseQuerySummaryWhenAvailable() {
    // given
    DbRequest dbRequest = new DbRequest();

    // Needs to be lenient because not called during this test under old semconv mode
    lenient().when(dbAttributesGetter.getDbQuerySummary(dbRequest)).thenReturn("SELECT users");
    // Needs to be lenient because not called during this test under new semconv mode
    lenient().when(dbAttributesGetter.getDbOperationName(dbRequest)).thenReturn("SELECT");
    lenient().when(dbAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(dbAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName)
        .isEqualTo(emitStableDatabaseSemconv() ? "SELECT users" : "SELECT database");
  }

  @Test
  void shouldExtractFullSpanNameForBatch() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(Arrays.asList("INSERT INTO table VALUES(1)", "INSERT INTO table VALUES(2)"));
    when(sqlAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo(emitStableDatabaseSemconv() ? "BATCH INSERT table" : "database");
  }

  @Test
  void shouldExtractFullSpanNameForSingleQueryBatch() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(singleton("INSERT INTO table VALUES(?)"));
    when(sqlAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");
    if (emitStableDatabaseSemconv()) {
      when(sqlAttributesGetter.getDbOperationBatchSize(dbRequest)).thenReturn(2L);
    }

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName)
        .isEqualTo(emitStableDatabaseSemconv() ? "BATCH INSERT table" : "INSERT database.table");
  }

  @Test
  void shouldFallBackToExplicitOperationNameForEmptySqlQuery() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest)).thenReturn(emptyList());
    when(sqlAttributesGetter.getDbOperationName(dbRequest)).thenReturn("WRITE");
    when(sqlAttributesGetter.getDbNamespace(dbRequest)).thenReturn("mydb");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("WRITE mydb");
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated method
  void shouldPreserveOldSemconvSpanNameForMigration() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(singleton("SELECT * from table"));
    lenient().when(sqlAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest =
        DbClientSpanNameExtractor.createForMigration(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName)
        .isEqualTo(emitStableDatabaseSemconv() ? "SELECT table" : "SELECT database");
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated method
  void shouldFallBackToExplicitOperationForEmptySqlQueryInMigration() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest)).thenReturn(emptyList());
    when(sqlAttributesGetter.getDbOperationName(dbRequest)).thenReturn("WRITE");
    when(sqlAttributesGetter.getDbNamespace(dbRequest)).thenReturn("mydb");

    SpanNameExtractor<DbRequest> underTest =
        DbClientSpanNameExtractor.createForMigration(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("WRITE mydb");
  }

  static class DbRequest {}
}
