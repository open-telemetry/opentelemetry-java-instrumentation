/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbClientSpanNameExtractorTest {
  @Mock DbClientAttributesGetter<DbRequest, Void> dbAttributesGetter;

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  SqlClientAttributesGetter<DbRequest, Void> sqlAttributesGetter;

  @BeforeEach
  void setUp() {
    lenient()
        .when(sqlAttributesGetter.getSqlDialect(any()))
        .thenReturn(DOUBLE_QUOTES_ARE_STRING_LITERALS);
    lenient().when(sqlAttributesGetter.getDbOperationBatchSize(any())).thenReturn(null);
  }

  @Test
  void shouldExtractFullSpanName() {
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
  void shouldSkipNamespaceIfTableAlreadyHasNamespacePrefix() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(singleton("SELECT * from another.table"));
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
  void shouldPreferCollectionNameOverNamespace() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(dbAttributesGetter.getDbOperationName(dbRequest)).thenReturn("SELECT");
    lenient().when(dbAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");
    when(dbAttributesGetter.getDbCollectionName(dbRequest)).thenReturn("users");
    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(dbAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("SELECT users");
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
  void shouldUseConfiguredTargetWithoutAddingAPort() {
    DbRequest dbRequest = new DbRequest();
    when(dbAttributesGetter.getServerAddress(dbRequest))
        .thenReturn("db1.example:5432,db2.example:5432");
    when(dbAttributesGetter.getServerPort(dbRequest)).thenReturn(null);
    String spanName = DbClientSpanNameExtractor.create(dbAttributesGetter).extract(dbRequest);

    assertThat(spanName).isEqualTo("db1.example:5432,db2.example:5432");
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

    when(dbAttributesGetter.getDbQuerySummary(dbRequest)).thenReturn("SELECT users");
    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(dbAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("SELECT users");
  }

  @Test
  void shouldExtractFullSpanNameForBatch() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(asList("INSERT INTO table VALUES(1)", "INSERT INTO table VALUES(2)"));
    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("BATCH INSERT table");
  }

  @Test
  void shouldExtractFullSpanNameForSingleQueryBatch() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(singleton("INSERT INTO table VALUES(?)"));
    when(sqlAttributesGetter.getDbOperationBatchSize(dbRequest)).thenReturn(2L);
    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("BATCH INSERT table");
  }

  @Test
  void shouldExtractFullSpanNameForSingleQueryEmptyBatch() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(singleton("INSERT INTO table VALUES(?)"));
    when(sqlAttributesGetter.getDbOperationBatchSize(dbRequest)).thenReturn(0L);
    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("BATCH INSERT table");
  }

  @Test
  void shouldFallBackToNamespaceForEmptySqlQuery() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest)).thenReturn(emptyList());
    when(sqlAttributesGetter.getDbNamespace(dbRequest)).thenReturn("mydb");
    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("mydb");
  }

  @Test
  void shouldExtractBatchSpanNameForEmptySqlQueryBatch() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest)).thenReturn(emptyList());
    when(sqlAttributesGetter.getDbOperationBatchSize(dbRequest)).thenReturn(0L);
    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("BATCH");
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated method
  void shouldPreserveOldSemconvSpanNameForMigration() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(singleton("SELECT * from table"));
    SpanNameExtractor<DbRequest> underTest =
        DbClientSpanNameExtractor.createWithGenericOldSpanName(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("SELECT table");
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated method
  void shouldFallBackToNamespaceForEmptySqlQueryInMigration() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest)).thenReturn(emptyList());
    when(sqlAttributesGetter.getDbNamespace(dbRequest)).thenReturn("mydb");
    SpanNameExtractor<DbRequest> underTest =
        DbClientSpanNameExtractor.createWithGenericOldSpanName(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("mydb");
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated method
  void shouldExtractBatchSpanNameForEmptySqlQueryBatchInMigration() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest)).thenReturn(emptyList());
    when(sqlAttributesGetter.getDbOperationBatchSize(dbRequest)).thenReturn(0L);
    SpanNameExtractor<DbRequest> underTest =
        DbClientSpanNameExtractor.createWithGenericOldSpanName(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertThat(spanName).isEqualTo("BATCH");
  }

  static class DbRequest {}
}
