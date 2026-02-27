/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
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

class SqlQuerySanitizerUtilCacheTest {
  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void testSqlSanitizerCaching() {
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
            return DOUBLE_QUOTES_ARE_STRING_LITERALS;
          }
        };
    SpanNameExtractor<Object> spanNameExtractor = DbClientSpanNameExtractor.create(getter);
    AttributesExtractor<Object, Void> attributesExtractor =
        SqlClientAttributesExtractor.create(getter);

    InstrumenterContext.reset();
    cleanup.deferCleanup(InstrumenterContext::reset);

    assertThat(spanNameExtractor.extract(null)).isEqualTo("SELECT test");
    // verify that analyzed query was cached
    SqlQuery cached = SqlQueryAnalyzerUtil.analyze(testQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    assertThat(cached)
        .isSameAs(SqlQueryAnalyzerUtil.analyze(testQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS));

    // verify that the attributes extractor produces correct values
    AttributeKey<String> queryTextKey =
        emitStableDatabaseSemconv()
            ? AttributeKey.stringKey("db.query.text")
            : AttributeKey.stringKey("db.statement");
    {
      AttributesBuilder builder = Attributes.builder();
      attributesExtractor.onStart(builder, Context.root(), null);
      assertThat(builder.build().get(queryTextKey)).isEqualTo("SELECT name FROM test WHERE id = ?");
    }
  }
}
