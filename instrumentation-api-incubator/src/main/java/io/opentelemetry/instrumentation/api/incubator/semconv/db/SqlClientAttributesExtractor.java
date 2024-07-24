/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/database/database-spans.md">database
 * attributes</a>. This class is designed with SQL (or SQL-like) database clients in mind.
 *
 * <p>It sets the same set of attributes as {@link DbClientAttributesExtractor} plus an additional
 * <code>db.sql.table</code> attribute. The raw SQL statements returned by the {@link
 * SqlClientAttributesGetter#getRawStatement(Object)} method are sanitized before use, all statement
 * parameters are removed.
 */
public final class SqlClientAttributesExtractor<REQUEST, RESPONSE>
    extends DbClientCommonAttributesExtractor<
        REQUEST, RESPONSE, SqlClientAttributesGetter<REQUEST>> {

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation");
  private static final AttributeKey<String> DB_STATEMENT = AttributeKey.stringKey("db.statement");

  /** Creates the SQL client attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
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

  private static final String SQL_CALL = "CALL";
  // sanitizer is also used to extract operation and table name, so we have it always enable here
  private static final SqlStatementSanitizer sanitizer = SqlStatementSanitizer.create(true);

  private final AttributeKey<String> dbTableAttribute;
  private final boolean statementSanitizationEnabled;

  SqlClientAttributesExtractor(
      SqlClientAttributesGetter<REQUEST> getter,
      AttributeKey<String> dbTableAttribute,
      boolean statementSanitizationEnabled) {
    super(getter);
    this.dbTableAttribute = dbTableAttribute;
    this.statementSanitizationEnabled = statementSanitizationEnabled;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    super.onStart(attributes, parentContext, request);

    String rawStatement = getter.getRawStatement(request);
    SqlStatementInfo sanitizedStatement = sanitizer.sanitize(rawStatement);
    String operation = sanitizedStatement.getOperation();
    internalSet(
        attributes,
        DB_STATEMENT,
        statementSanitizationEnabled ? sanitizedStatement.getFullStatement() : rawStatement);
    internalSet(attributes, DB_OPERATION, operation);
    if (!SQL_CALL.equals(operation)) {
      internalSet(attributes, dbTableAttribute, sanitizedStatement.getMainIdentifier());
    }
  }
}
