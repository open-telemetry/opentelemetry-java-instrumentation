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
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/database/database-spans.md">database
 * attributes</a>. This class is designed with SQL (or SQL-like) database clients in mind.
 *
 * <p>It sets the same set of attributes as {@link DbClientAttributesExtractor} plus an additional
 * <code>db.sql.table</code> attribute. The raw SQL statements returned by the {@link
 * SqlClientAttributesGetter#getRawQueryText(Object)} method are sanitized before use, all statement
 * parameters are removed.
 */
public final class SqlClientAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE>, SpanKeyProvider {

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation");
  private static final AttributeKey<String> DB_OPERATION_NAME =
      AttributeKey.stringKey("db.operation.name");
  private static final AttributeKey<String> DB_STATEMENT = AttributeKey.stringKey("db.statement");
  private static final AttributeKey<String> DB_QUERY_TEXT = AttributeKey.stringKey("db.query.text");
  private static final AttributeKey<String> DB_COLLECTION_NAME =
      AttributeKey.stringKey("db.collection.name");

  /** Creates the SQL client attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      SqlClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return SqlClientAttributesExtractor.<REQUEST, RESPONSE>builder(getter).build();
  }

  /**
   * Returns a new {@link SqlClientAttributesExtractorBuilder} that can be used to configure the SQL
   * client attributes extractor.
   */
  public static <REQUEST, RESPONSE> SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      SqlClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return new SqlClientAttributesExtractorBuilder<>(getter);
  }

  private static final String SQL_CALL = "CALL";
  // sanitizer is also used to extract operation and table name, so we have it always enable here
  private static final SqlStatementSanitizer sanitizer = SqlStatementSanitizer.create(true);

  private final AttributeKey<String> oldSemconvTableAttribute;
  private final boolean statementSanitizationEnabled;

  private final AttributesExtractor<REQUEST, RESPONSE> delegate;
  private final SqlClientAttributesGetter<REQUEST, RESPONSE> getter;

  SqlClientAttributesExtractor(
      SqlClientAttributesGetter<REQUEST, RESPONSE> getter,
      AttributeKey<String> oldSemconvTableAttribute,
      boolean statementSanitizationEnabled) {
    this.delegate = DbClientAttributesExtractor.create(getter);
    this.oldSemconvTableAttribute = oldSemconvTableAttribute;
    this.statementSanitizationEnabled = statementSanitizationEnabled;
    this.getter = getter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    delegate.onStart(attributes, parentContext, request);

    String rawQueryText = getter.getRawQueryText(request);
    SqlStatementInfo sanitizedStatement = sanitizer.sanitize(rawQueryText);
    String operation = sanitizedStatement.getOperation();
    if (SemconvStability.emitStableDatabaseSemconv()) {
      internalSet(
          attributes,
          DB_QUERY_TEXT,
          statementSanitizationEnabled ? sanitizedStatement.getFullStatement() : rawQueryText);
      internalSet(attributes, DB_OPERATION_NAME, operation);
    }
    if (SemconvStability.emitOldDatabaseSemconv()) {
      internalSet(
          attributes,
          DB_STATEMENT,
          statementSanitizationEnabled ? sanitizedStatement.getFullStatement() : rawQueryText);
      internalSet(attributes, DB_OPERATION, operation);
    }
    if (!SQL_CALL.equals(operation)) {
      if (SemconvStability.emitStableDatabaseSemconv()) {
        internalSet(attributes, DB_COLLECTION_NAME, sanitizedStatement.getMainIdentifier());
      }
      if (SemconvStability.emitOldDatabaseSemconv()) {
        internalSet(attributes, oldSemconvTableAttribute, sanitizedStatement.getMainIdentifier());
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    delegate.onEnd(attributes, context, request, response, error);
  }

  /**
   * This method is internal and is hence not for public use. Its API is unstable and can change at
   * any time.
   */
  @Override
  public SpanKey internalGetSpanKey() {
    return SpanKey.DB_CLIENT;
  }
}
