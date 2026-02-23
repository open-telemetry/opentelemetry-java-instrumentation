/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuerySanitizer.CacheKey;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.InstrumenterContext;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SqlQuerySanitizerUtilCacheTest {
  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @SuppressWarnings("deprecation") // using deprecated DB_SQL_TABLE
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
    // verify that sanitized query was cached
    SqlQuery cached = SqlQuerySanitizerUtil.sanitize(testQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    assertThat(cached)
        .isSameAs(SqlQuerySanitizerUtil.sanitize(testQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS));

    // replace cached sanitization result to verify it is used by the attributes extractor
    CacheKey cacheKey = CacheKey.create(testQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    @SuppressWarnings("unchecked")
    Map<CacheKey, SqlQuery> sanitizedMap =
        (Map<CacheKey, SqlQuery>)
            (Map<?, ?>)
                InstrumenterContext.computeIfAbsent("sanitized-sql-map", unused -> new HashMap<>());
    sanitizedMap.put(
        cacheKey, SqlQuery.create("SELECT name2 FROM test2 WHERE id = ?", "SELECT", "test2"));
    {
      AttributesBuilder builder = Attributes.builder();
      attributesExtractor.onStart(builder, Context.root(), null);
      assertThat(builder.build().get(maybeStable(DB_SQL_TABLE))).isEqualTo("test2");
    }

    // clear cached value to see whether it gets recomputed correctly
    sanitizedMap.clear();
    {
      AttributesBuilder builder = Attributes.builder();
      attributesExtractor.onStart(builder, Context.root(), null);
      assertThat(builder.build().get(maybeStable(DB_SQL_TABLE))).isEqualTo("test");
    }
  }
}
