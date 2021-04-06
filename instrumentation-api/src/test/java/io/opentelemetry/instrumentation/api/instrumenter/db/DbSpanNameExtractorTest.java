/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbSpanNameExtractorTest {
  @Mock DbAttributesExtractor<DbRequest> dbAttributesExtractor;
  @Mock SqlAttributesExtractor<DbRequest> sqlAttributesExtractor;
  @Mock SpanNameExtractor<DbRequest> defaultSpanNameExtractor;

  @Test
  void shouldExtractFullSpanName() {
    // given
    DbRequest dbRequest = new DbRequest();

    // cannot stub dbOperation() and dbTable() because they're final
    given(sqlAttributesExtractor.rawDbStatement(dbRequest)).willReturn("SELECT * FROM table");
    given(sqlAttributesExtractor.dbName(dbRequest)).willReturn("database");

    SpanNameExtractor<DbRequest> underTest =
        new DbSpanNameExtractor<>(sqlAttributesExtractor, defaultSpanNameExtractor);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT database.table", spanName);

    then(defaultSpanNameExtractor).shouldHaveNoInteractions();
  }

  @Test
  void shouldExtractOperationAndTable() {
    // given
    DbRequest dbRequest = new DbRequest();

    // cannot stub dbOperation() and dbTable() because they're final
    given(sqlAttributesExtractor.rawDbStatement(dbRequest)).willReturn("SELECT * FROM table");

    SpanNameExtractor<DbRequest> underTest =
        new DbSpanNameExtractor<>(sqlAttributesExtractor, defaultSpanNameExtractor);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT table", spanName);

    then(defaultSpanNameExtractor).shouldHaveNoInteractions();
  }

  @Test
  void shouldExtractOperationAndName() {
    // given
    DbRequest dbRequest = new DbRequest();

    given(dbAttributesExtractor.dbOperation(dbRequest)).willReturn("SELECT");
    given(dbAttributesExtractor.dbName(dbRequest)).willReturn("database");

    SpanNameExtractor<DbRequest> underTest =
        new DbSpanNameExtractor<>(dbAttributesExtractor, defaultSpanNameExtractor);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT database", spanName);

    then(defaultSpanNameExtractor).shouldHaveNoInteractions();
  }

  @Test
  void shouldExtractOperation() {
    // given
    DbRequest dbRequest = new DbRequest();

    given(dbAttributesExtractor.dbOperation(dbRequest)).willReturn("SELECT");

    SpanNameExtractor<DbRequest> underTest =
        new DbSpanNameExtractor<>(dbAttributesExtractor, defaultSpanNameExtractor);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("SELECT", spanName);

    then(defaultSpanNameExtractor).shouldHaveNoInteractions();
  }

  @Test
  void shouldExtractDbName() {
    // given
    DbRequest dbRequest = new DbRequest();

    given(dbAttributesExtractor.dbName(dbRequest)).willReturn("database");

    SpanNameExtractor<DbRequest> underTest =
        new DbSpanNameExtractor<>(dbAttributesExtractor, defaultSpanNameExtractor);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("database", spanName);

    then(defaultSpanNameExtractor).shouldHaveNoInteractions();
  }

  @Test
  void shouldFallBackToDefaultSpanName() {
    // given
    DbRequest dbRequest = new DbRequest();

    given(defaultSpanNameExtractor.extract(dbRequest)).willReturn("DB Query");

    SpanNameExtractor<DbRequest> underTest =
        new DbSpanNameExtractor<>(dbAttributesExtractor, defaultSpanNameExtractor);

    // when
    String spanName = underTest.extract(dbRequest);

    // then
    assertEquals("DB Query", spanName);
  }

  static class DbRequest {}
}
