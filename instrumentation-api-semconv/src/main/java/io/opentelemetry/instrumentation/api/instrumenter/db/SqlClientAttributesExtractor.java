/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.db.SqlStatementInfo;
import io.opentelemetry.instrumentation.api.db.SqlStatementSanitizer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/database.md">database
 * attributes</a>. This class is designed with SQL (or SQL-like) database clients in mind.
 *
 * <p>It sets the same set of attributes as {@link DbClientAttributesExtractor} plus an additional
 * <code>{@linkplain SemanticAttributes#DB_SQL_TABLE db.sql.table}</code> attrubute. The raw SQL
 * statements returned by the {@link SqlClientAttributesGetter#rawStatement(Object)} method are
 * sanitized before use, all statement parameters are removed.
 */
public final class SqlClientAttributesExtractor<REQUEST, RESPONSE>
    extends DbClientCommonAttributesExtractor<
        REQUEST, RESPONSE, SqlClientAttributesGetter<REQUEST>> {

  /** Creates the SQL client attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> SqlClientAttributesExtractor<REQUEST, RESPONSE> create(
      SqlClientAttributesGetter<REQUEST> getter) {
    return SqlClientAttributesExtractor.<REQUEST, RESPONSE>builder(getter).build();
  }

  /**
   * Returns a new {@link SqlClientAttributesExtractorBuilder} that can be used to configure the SQL
   * client attributes extractor.
   */
  public static <REQUEST, RESPONSE> SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      SqlClientAttributesGetter<REQUEST> getter) {
    return new SqlClientAttributesExtractorBuilder<>(getter);
  }

  private final AttributeKey<String> dbTableAttribute;

  SqlClientAttributesExtractor(
      SqlClientAttributesGetter<REQUEST> getter, AttributeKey<String> dbTableAttribute) {
    super(getter);
    this.dbTableAttribute = dbTableAttribute;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    super.onStart(attributes, parentContext, request);

    SqlStatementInfo sanitizedStatement =
        SqlStatementSanitizer.sanitize(getter.rawStatement(request));
    set(attributes, SemanticAttributes.DB_STATEMENT, sanitizedStatement.getFullStatement());
    set(attributes, SemanticAttributes.DB_OPERATION, sanitizedStatement.getOperation());
    set(attributes, dbTableAttribute, sanitizedStatement.getTable());
  }
}
