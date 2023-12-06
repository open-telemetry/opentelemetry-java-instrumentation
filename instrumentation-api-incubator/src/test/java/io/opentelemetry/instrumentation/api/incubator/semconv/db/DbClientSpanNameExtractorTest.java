/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbClientSpanNameExtractorTest {
  @Mock DbClientAttributeGetter<DbRequest> dbAttributeGetter;
  @Mock SqlClientAttributeGetter<DbRequest> sqlAttributeGetter;

  @Test
  void shouldExtractFullSpanName() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributeGetter.getRawStatement(dbRequest)).thenReturn("SELECT * from table");
    when(sqlAttributeGetter.getName(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributeGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT database.table", spanName);
  }

  @Test
  void shouldSkipDbNameIfTableAlreadyHasDbNamePrefix() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributeGetter.getRawStatement(dbRequest)).thenReturn("SELECT * from another.table");
    when(sqlAttributeGetter.getName(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributeGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT another.table", spanName);
  }

  @Test
  void shouldExtractOperationAndTable() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(sqlAttributeGetter.getRawStatement(dbRequest)).thenReturn("SELECT * from table");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(sqlAttributeGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT table", spanName);
  }

  @Test
  void shouldExtractOperationAndName() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(dbAttributeGetter.getOperation(dbRequest)).thenReturn("SELECT");
    when(dbAttributeGetter.getName(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(dbAttributeGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT database", spanName);
  }

  @Test
  void shouldExtractOperation() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(dbAttributeGetter.getOperation(dbRequest)).thenReturn("SELECT");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(dbAttributeGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT", spanName);
  }

  @Test
  void shouldExtractDbName() {
    // given
    DbRequest dbRequest = new DbRequest();

    when(dbAttributeGetter.getName(dbRequest)).thenReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(dbAttributeGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("database", spanName);
  }

  @Test
  void shouldFallBackToDefaultSpanName() {
    // given
    DbRequest dbRequest = new DbRequest();

    SpanNameExtractor<DbRequest> underTest = DbClientSpanNameExtractor.create(dbAttributeGetter);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("DB Query", spanName);
  }

  static class DbRequest {}
}
