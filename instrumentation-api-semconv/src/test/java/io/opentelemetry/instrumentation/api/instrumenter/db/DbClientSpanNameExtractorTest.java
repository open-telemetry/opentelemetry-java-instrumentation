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
class DbClientSpanNameExtractorTest {
  @Mock DbClientAttributesGetter<DbRequest> dbAttributesGetter;
  @Mock SqlClientAttributesGetter<DbRequest> sqlAttributesGetter;

  @Test
  void shouldExtractFullSpanName() {
    // given
    DbRequest dbRequest = new DbRequest();

    given(sqlAttributesGetter.rawStatement(dbRequest)).willReturn("SELECT * from table");
    given(sqlAttributesGetter.name(dbRequest)).willReturn("database");

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

    given(sqlAttributesGetter.rawStatement(dbRequest)).willReturn("SELECT * from another.table");
    given(sqlAttributesGetter.name(dbRequest)).willReturn("database");

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

    given(sqlAttributesGetter.rawStatement(dbRequest)).willReturn("SELECT * from table");

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

    given(dbAttributesGetter.operation(dbRequest)).willReturn("SELECT");
    given(dbAttributesGetter.name(dbRequest)).willReturn("database");

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

    given(dbAttributesGetter.operation(dbRequest)).willReturn("SELECT");

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

    given(dbAttributesGetter.name(dbRequest)).willReturn("database");

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

  static class DbRequest {}
}
