/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbSpanNameExtractorTest {
  @Mock DbAttributesExtractor<DbRequest> dbAttributesExtractor;
  @Mock SqlAttributesExtractor<DbRequest> sqlAttributesExtractor;

  @Test
  void shouldExtractFullSpanName() {
    // given
    DbRequest dbRequest = new DbRequest();

    // cannot stub dbOperation() and dbTable() because they're final
    given(sqlAttributesExtractor.rawDbStatement(dbRequest)).willReturn("SELECT * FROM table");
    given(sqlAttributesExtractor.dbName(dbRequest)).willReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbSpanNameExtractor.create(sqlAttributesExtractor);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT database.table", spanName);
  }

  @Test
  void shouldExtractOperationAndTable() {
    // given
    DbRequest dbRequest = new DbRequest();

    // cannot stub dbOperation() and dbTable() because they're final
    given(sqlAttributesExtractor.rawDbStatement(dbRequest)).willReturn("SELECT * FROM table");

    SpanNameExtractor<DbRequest> underTest = DbSpanNameExtractor.create(sqlAttributesExtractor);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT table", spanName);
  }

  @Test
  void shouldExtractOperationAndName() {
    // given
    DbRequest dbRequest = new DbRequest();

    given(dbAttributesExtractor.dbOperation(dbRequest)).willReturn("SELECT");
    given(dbAttributesExtractor.dbName(dbRequest)).willReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbSpanNameExtractor.create(dbAttributesExtractor);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT database", spanName);
  }

  @Test
  void shouldExtractOperation() {
    // given
    DbRequest dbRequest = new DbRequest();

    given(dbAttributesExtractor.dbOperation(dbRequest)).willReturn("SELECT");

    SpanNameExtractor<DbRequest> underTest = DbSpanNameExtractor.create(dbAttributesExtractor);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT", spanName);
  }

  @Test
  void shouldExtractDbName() {
    // given
    DbRequest dbRequest = new DbRequest();

    given(dbAttributesExtractor.dbName(dbRequest)).willReturn("database");

    SpanNameExtractor<DbRequest> underTest = DbSpanNameExtractor.create(dbAttributesExtractor);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("database", spanName);
  }

  @Test
  void shouldFallBackToDefaultSpanName() {
    // given
    DbRequest dbRequest = new DbRequest();

    SpanNameExtractor<DbRequest> underTest = DbSpanNameExtractor.create(dbAttributesExtractor);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("DB Query", spanName);
  }

  static class DbRequest {}
}
