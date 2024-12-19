/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.MultiQuerySqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbClientSpanNameExtractorTest {
  @Mock DbClientAttributesGetter<DbRequest> dbAttributesGetter;
  @Mock SqlClientAttributesGetter<DbRequest> sqlAttributesGetter;
  @Mock MultiQuerySqlClientAttributesGetter<DbRequest> multiQuerySqlClientAttributesGetter;

  @Test
  void shouldExtractFullSpanName() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryText(dbRequest)).thenReturn("SELECT * from table");
    when(sqlAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT database.table", spanName);
  }

  @Test
  void shouldSkipDbNameIfTableAlreadyHasDbNamePrefix() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryText(dbRequest)).thenReturn("SELECT * from another.table");
    when(sqlAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT another.table", spanName);
  }

  @Test
  void shouldExtractOperationAndTable() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryText(dbRequest)).thenReturn("SELECT * from table");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT table", spanName);
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
    assertEquals("SELECT database", spanName);
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
    assertEquals("SELECT", spanName);
  }

  @Test
  void shouldExtractDbName() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(dbAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(dbAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("database", spanName);
  }

  @Test
  void shouldFallBackToDefaultSpanName() {
    // given
    DbRequest dbRequest = new DbRequest();

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(dbAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("DB Query", spanName);
  }

  @Test
  void shouldExtractFullSpanNameForBatch() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(multiQuerySqlClientAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(Arrays.asList("INSERT INTO table VALUES(1)", "INSERT INTO table VALUES(2)"));
    when(multiQuerySqlClientAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");
    when(multiQuerySqlClientAttributesGetter.getBatchSize(dbRequest)).thenReturn(2L);

    SpanNameExtractor<DbRequest> underTest =
        DbClientSpanNameExtractor.create(multiQuerySqlClientAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals(
        SemconvStability.emitStableDatabaseSemconv() ? "BATCH INSERT database.table" : "database",
        spanName);
  }

  @Test
  void shouldExtractFullSpanNameForSingleQueryBatch() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(multiQuerySqlClientAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(Collections.singletonList("INSERT INTO table VALUES(?)"));
    if (SemconvStability.emitStableDatabaseSemconv()) {
      when(multiQuerySqlClientAttributesGetter.getRawQueryText(dbRequest))
          .thenReturn("INSERT INTO table VALUES(?)");
    }
    when(multiQuerySqlClientAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");
    when(multiQuerySqlClientAttributesGetter.getBatchSize(dbRequest)).thenReturn(2L);

    SpanNameExtractor<DbRequest> underTest =
        DbClientSpanNameExtractor.create(multiQuerySqlClientAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals(
        SemconvStability.emitStableDatabaseSemconv() ? "BATCH INSERT database.table" : "database",
        spanName);
  }

  static class DbRequest {}
}
