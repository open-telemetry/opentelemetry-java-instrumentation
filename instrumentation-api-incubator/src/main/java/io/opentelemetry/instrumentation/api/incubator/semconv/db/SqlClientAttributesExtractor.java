/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.InternalNetworkAttributesExtractor;
import io.opentelemetry.semconv.AttributeKeyTemplate;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/db/database-spans.md">database
 * attributes</a>. This class is designed with SQL (or SQL-like) database clients in mind.
 *
 * <p>It sets the same set of attributes as {@link DbClientAttributesExtractor} plus an additional
 * <code>db.sql.table</code> attribute. The raw SQL statements returned by the {@link
 * SqlClientAttributesGetter#getRawQueryTexts(Object)} method are sanitized before use, all
 * statement parameters are removed.
 */
public final class SqlClientAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE>, SpanKeyProvider {

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation");
  private static final AttributeKey<String> DB_STATEMENT = AttributeKey.stringKey("db.statement");
  private static final AttributeKeyTemplate<String> DB_QUERY_PARAMETER =
      AttributeKeyTemplate.stringKeyTemplate("db.query.parameter");

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

  private final SqlClientAttributesGetter<REQUEST, RESPONSE> getter;
  private final InternalNetworkAttributesExtractor<REQUEST, RESPONSE> internalNetworkExtractor;
  private final ServerAttributesExtractor<REQUEST, RESPONSE> serverAttributesExtractor;
  private final AttributeKey<String> oldSemconvTableAttribute;
  private final boolean statementSanitizationEnabled;
  private final boolean captureQueryParameters;
  private final boolean statementSanitizationAnsiQuotes;

  SqlClientAttributesExtractor(
      SqlClientAttributesGetter<REQUEST, RESPONSE> getter,
      AttributeKey<String> oldSemconvTableAttribute,
      boolean statementSanitizationEnabled,
      boolean statementSanitizationAnsiQuotes,
      boolean captureQueryParameters) {
    this.getter = getter;
    this.oldSemconvTableAttribute = oldSemconvTableAttribute;
    // capturing query parameters disables statement sanitization
    this.statementSanitizationEnabled = !captureQueryParameters && statementSanitizationEnabled;
    this.captureQueryParameters = captureQueryParameters;
    this.statementSanitizationAnsiQuotes = statementSanitizationAnsiQuotes;
    internalNetworkExtractor = new InternalNetworkAttributesExtractor<>(getter, true, false);
    serverAttributesExtractor = ServerAttributesExtractor.create(getter);
  }

  @SuppressWarnings("deprecation") // until old db semconv are dropped
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    Collection<String> rawQueryTexts = getter.getRawQueryTexts(request);
    SqlDialect dialect =
        SqlStatementSanitizerUtil.getDialect(
            getter.getDbSystem(request), statementSanitizationAnsiQuotes);

    Long batchSize = getter.getBatchSize(request);
    boolean isBatch = batchSize != null && batchSize > 1;

    if (SemconvStability.emitOldDatabaseSemconv()) {
      if (rawQueryTexts.size() == 1) { // for backcompat(?)
        String rawQueryText = rawQueryTexts.iterator().next();
        SqlStatementInfo sanitizedStatement =
            SqlStatementSanitizerUtil.sanitize(rawQueryText, dialect);
        String operation = sanitizedStatement.getOperation();
        internalSet(
            attributes,
            DB_STATEMENT,
            statementSanitizationEnabled ? sanitizedStatement.getFullStatement() : rawQueryText);
        internalSet(attributes, DB_OPERATION, operation);
        if (!SQL_CALL.equals(operation)) {
          internalSet(attributes, oldSemconvTableAttribute, sanitizedStatement.getMainIdentifier());
        }
      }
    }

    if (SemconvStability.emitStableDatabaseSemconv()) {
      if (isBatch) {
        internalSet(attributes, DB_OPERATION_BATCH_SIZE, batchSize);
      }
      if (rawQueryTexts.size() == 1) {
        String rawQueryText = rawQueryTexts.iterator().next();
        SqlStatementInfo sanitizedStatement =
            SqlStatementSanitizerUtil.sanitize(rawQueryText, dialect);
        String operation = sanitizedStatement.getOperation();
        internalSet(
            attributes,
            DB_QUERY_TEXT,
            statementSanitizationEnabled ? sanitizedStatement.getFullStatement() : rawQueryText);
        internalSet(attributes, DB_OPERATION_NAME, isBatch ? "BATCH " + operation : operation);
        if (!SQL_CALL.equals(operation)) {
          internalSet(attributes, DB_COLLECTION_NAME, sanitizedStatement.getMainIdentifier());
        }
      } else if (rawQueryTexts.size() > 1) {
        MultiQuery multiQuery =
            MultiQuery.analyze(
                getter.getRawQueryTexts(request), dialect, statementSanitizationEnabled);
        internalSet(attributes, DB_QUERY_TEXT, join("; ", multiQuery.getStatements()));

        String operation =
            multiQuery.getOperation() != null ? "BATCH " + multiQuery.getOperation() : "BATCH";
        internalSet(attributes, DB_OPERATION_NAME, operation);

        if (multiQuery.getMainIdentifier() != null
            && (multiQuery.getOperation() == null || !SQL_CALL.equals(multiQuery.getOperation()))) {
          internalSet(attributes, DB_COLLECTION_NAME, multiQuery.getMainIdentifier());
        }
      }
    }

    Map<String, String> queryParameters = getter.getQueryParameters(request);
    setQueryParameters(attributes, isBatch, queryParameters);

    // calling this last so explicit getDbOperationName(), getDbCollectionName(),
    // getDbQueryText(), and getDbQuerySummary() implementations can override
    // the parsed values from above
    DbClientAttributesExtractor.onStartCommon(attributes, getter, request);
    serverAttributesExtractor.onStart(attributes, parentContext, request);
  }

  private void setQueryParameters(
      AttributesBuilder attributes, boolean isBatch, Map<String, String> queryParameters) {
    if (captureQueryParameters && !isBatch && queryParameters != null) {
      for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        internalSet(attributes, DB_QUERY_PARAMETER.getAttributeKey(key), value);
      }
    }
  }

  // String.join is not available on android
  private static String join(String delimiter, Collection<String> collection) {
    StringBuilder builder = new StringBuilder();
    for (String string : collection) {
      if (builder.length() != 0) {
        builder.append(delimiter);
      }
      builder.append(string);
    }
    return builder.toString();
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    internalNetworkExtractor.onEnd(attributes, request, response);
    DbClientAttributesExtractor.onEndCommon(attributes, getter, response, error);
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
