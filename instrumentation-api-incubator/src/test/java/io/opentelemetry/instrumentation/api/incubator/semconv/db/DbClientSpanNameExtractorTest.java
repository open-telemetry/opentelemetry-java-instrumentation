/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.ExtractQuerySummaryMarker;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbClientSpanNameExtractorTest {
  @Mock DbClientAttributesGetter<DbRequest, Void> dbAttributesGetter;

  @Mock(extraInterfaces = ExtractQuerySummaryMarker.class)
  SqlClientAttributesGetter<DbRequest, Void> sqlAttributesGetter;

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
    assertEquals(emitStableDatabaseSemconv() ? "SELECT table" : "SELECT database.table", spanName);
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
    assertEquals("SELECT another.table", spanName);
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
  void shouldExtractNamespace() {
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
    assertEquals(emitStableDatabaseSemconv() ? "SELECT users" : "SELECT database", spanName);
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
    assertEquals(
        SemconvStability.emitStableDatabaseSemconv() ? "BATCH INSERT table" : "database", spanName);
  }

  @Test
  void shouldExtractFullSpanNameForSingleQueryBatch() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributesGetter.getRawQueryTexts(dbRequest))
        .thenReturn(singleton("INSERT INTO table VALUES(?)"));
    when(sqlAttributesGetter.getDbNamespace(dbRequest)).thenReturn("database");
    if (SemconvStability.emitStableDatabaseSemconv()) {
      when(sqlAttributesGetter.getDbOperationBatchSize(dbRequest)).thenReturn(2L);
    }

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributesGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals(
        SemconvStability.emitStableDatabaseSemconv()
            ? "BATCH INSERT table"
            : "INSERT database.table",
        spanName);
  }

  static class DbRequest {}
}
