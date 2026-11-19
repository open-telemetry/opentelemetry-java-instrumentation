/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.InstrumenterContext;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SqlQueryAnalyzerUtilCacheTest {
  private static final SqlDialect TEST_DIALECT =
      SqlDialect.builder().setDoubleQuotesAreIdentifiers(false).build();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void testSqlAnalysisCaching() {
    String testQuery = "SELECT name FROM test WHERE id = 1";
    SqlClientAttributesGetter<Object, Void> getter =
        new SqlClientAttributesGetter<Object, Void>() {
          @Override
          public String getDbSystemName(Object o) {
            return "testdb";
          }

          @Override
          public String getDbNamespace(Object o) {
            return null;
          }

          @Override
          public Collection<String> getRawQueryTexts(Object request) {
            return singletonList(testQuery);
          }

          @Override
          public SqlDialect getSqlDialect(Object request) {
            return TEST_DIALECT;
          }
        };
    SpanNameExtractor<Object> spanNameExtractor = DbClientSpanNameExtractor.create(getter);
    AttributesExtractor<Object, Void> attributesExtractor =
        SqlClientAttributesExtractor.create(getter);

    InstrumenterContext.reset();
    cleanup.deferCleanup(InstrumenterContext::reset);

    assertThat(spanNameExtractor.extract(null)).isEqualTo("SELECT test");
    // verify that analyzed query was cached
    SqlQuery cached = SqlQueryAnalyzerUtil.analyze(testQuery, TEST_DIALECT);
    assertThat(cached).isSameAs(SqlQueryAnalyzerUtil.analyze(testQuery, TEST_DIALECT));

    // verify that the attributes extractor produces correct values
    AttributeKey<String> queryTextKey = DB_QUERY_TEXT;
    {
      AttributesBuilder builder = Attributes.builder();
      attributesExtractor.onStart(builder, Context.root(), null);
      assertThat(builder.build().get(queryTextKey)).isEqualTo("SELECT name FROM test WHERE id = ?");
    }
  }

  @Test
  void analysisCacheIsScopedToInstrumenterCall() {
    String query = "SELECT * FROM test " + new String(new char[10 * 1024]).replace('\0', ' ');
    InstrumenterContext.reset();
    cleanup.deferCleanup(InstrumenterContext::reset);

    SqlQuery first = SqlQueryAnalyzerUtil.analyze(query, TEST_DIALECT);
    assertThat(SqlQueryAnalyzer.isCached(query, TEST_DIALECT)).isFalse();
    assertThat(SqlQueryAnalyzerUtil.analyze(query, TEST_DIALECT)).isSameAs(first);

    InstrumenterContext.reset();
    assertThat(SqlQueryAnalyzerUtil.analyze(query, TEST_DIALECT))
        .isEqualTo(first)
        .isNotSameAs(first);
  }
}
