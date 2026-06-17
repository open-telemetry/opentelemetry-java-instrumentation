/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.stableDbSystemName;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_STORED_PROCEDURE_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
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
  private static final AttributeKey<String> DB_NAME = AttributeKey.stringKey("db.name");
  private static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system");
  private static final AttributeKey<String> DB_USER = AttributeKey.stringKey("db.user");
  private static final AttributeKey<String> DB_CONNECTION_STRING =
      AttributeKey.stringKey("db.connection_string");
  private static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation");
  private static final AttributeKey<String> DB_STATEMENT = AttributeKey.stringKey("db.statement");
  private static final AttributeKeyTemplate<String> DB_QUERY_PARAMETER =
      AttributeKeyTemplate.stringKeyTemplate("db.query.parameter");

  /** Creates the SQL client attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      SqlClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return SqlClientAttributesExtractor.builder(getter).build();
  }

  /**
   * Returns a new {@link SqlClientAttributesExtractorBuilder} that can be used to configure the SQL
   * client attributes extractor.
   */
  public static <REQUEST, RESPONSE> SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      SqlClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return new SqlClientAttributesExtractorBuilder<>(getter);
  }

  private final SqlClientAttributesGetter<REQUEST, RESPONSE> getter;
  private final InternalNetworkAttributesExtractor<REQUEST, RESPONSE> internalNetworkExtractor;
  private final ServerAttributesExtractor<REQUEST, RESPONSE> serverAttributesExtractor;
  @Nullable private final AttributeKey<String> oldSemconvTableAttribute;
  private final boolean querySanitizationEnabled;
  private final boolean captureQueryParameters;
  private final boolean singleOperationAndCollection;

  SqlClientAttributesExtractor(
      SqlClientAttributesGetter<REQUEST, RESPONSE> getter,
      @Nullable AttributeKey<String> oldSemconvTableAttribute,
      boolean querySanitizationEnabled,
      boolean captureQueryParameters,
      boolean singleOperationAndCollection) {
    this.getter = getter;
    this.oldSemconvTableAttribute = oldSemconvTableAttribute;
    // capturing query parameters disables query sanitization
    this.querySanitizationEnabled = !captureQueryParameters && querySanitizationEnabled;
    this.captureQueryParameters = captureQueryParameters;
    this.singleOperationAndCollection = singleOperationAndCollection;
    internalNetworkExtractor =
        new InternalNetworkAttributesExtractor<>(getter, emitOldDatabaseSemconv(), false);
    serverAttributesExtractor = ServerAttributesExtractor.create(getter);
  }

  @SuppressWarnings("deprecation") // until old db semconv are dropped
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    Collection<String> rawQueryTexts = getter.getRawQueryTexts(request);
    SqlDialect dialect = getter.getSqlDialect(request);

    Long batchSize = getter.getDbOperationBatchSize(request);
    boolean isBatch = batchSize != null && batchSize > 1;

    if (emitOldDatabaseSemconv()) {
      if (rawQueryTexts.size() == 1) { // for backcompat(?)
        String rawQueryText = rawQueryTexts.iterator().next();
        SqlQuery analyzedQuery = SqlQueryAnalyzerUtil.analyze(rawQueryText, dialect);
        String operationName = analyzedQuery.getOperationName();
        attributes.put(
            DB_STATEMENT, querySanitizationEnabled ? analyzedQuery.getQueryText() : rawQueryText);
        attributes.put(DB_OPERATION, operationName);
        if (oldSemconvTableAttribute != null) {
          attributes.put(oldSemconvTableAttribute, analyzedQuery.getCollectionName());
        }
      }
    }

    if (emitStableDatabaseSemconv()) {
      if (isBatch) {
        attributes.put(DB_OPERATION_BATCH_SIZE, batchSize);
      }
      if (rawQueryTexts.size() == 1) {
        String rawQueryText = rawQueryTexts.iterator().next();
        SqlQuery analyzedQuery = SqlQueryAnalyzerUtil.analyzeWithSummary(rawQueryText, dialect);
        boolean shouldSanitize =
            querySanitizationEnabled && !getter.isParameterizedQuery(request, 0);
        attributes.put(DB_QUERY_TEXT, shouldSanitize ? analyzedQuery.getQueryText() : rawQueryText);
        String querySummary = analyzedQuery.getQuerySummary();
        attributes.put(
            DB_QUERY_SUMMARY,
            isBatch && querySummary != null ? "BATCH " + querySummary : querySummary);
        if (singleOperationAndCollection) {
          attributes.put(DB_OPERATION_NAME, analyzedQuery.getOperationName());
          attributes.put(DB_COLLECTION_NAME, analyzedQuery.getCollectionName());
        }
        attributes.put(DB_STORED_PROCEDURE_NAME, analyzedQuery.getStoredProcedureName());
      } else if (rawQueryTexts.size() > 1) {
        MultiQuery.Builder builder = MultiQuery.builder();
        int queryIndex = 0;
        for (String rawQueryText : rawQueryTexts) {
          SqlQuery analyzedQuery = SqlQueryAnalyzerUtil.analyzeWithSummary(rawQueryText, dialect);
          boolean shouldSanitize =
              querySanitizationEnabled && !getter.isParameterizedQuery(request, queryIndex);
          builder.add(analyzedQuery, shouldSanitize ? analyzedQuery.getQueryText() : rawQueryText);
          queryIndex++;
        }
        MultiQuery multiQuery = builder.build();
        attributes.put(DB_QUERY_TEXT, join("; ", multiQuery.getQueryTexts()));
        attributes.put(DB_QUERY_SUMMARY, multiQuery.getQuerySummary());
        attributes.put(DB_STORED_PROCEDURE_NAME, multiQuery.getStoredProcedureName());
      }
    }

    if (emitStableDatabaseSemconv()) {
      attributes.put(DB_SYSTEM_NAME, stableDbSystemName(getter.getDbSystemName(request)));
      attributes.put(DB_NAMESPACE, getter.getDbNamespace(request));
    }
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_SYSTEM, getter.getDbSystem(request));
      attributes.put(DB_USER, getter.getUser(request));
      attributes.put(DB_NAME, getter.getDbName(request));
      attributes.put(DB_CONNECTION_STRING, getter.getConnectionString(request));
    }
    if (captureQueryParameters && !isBatch) {
      Map<String, String> queryParameters = getter.getDbQueryParameters(request);
      if (queryParameters != null && !queryParameters.isEmpty()) {
        for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
          attributes.put(DB_QUERY_PARAMETER.getAttributeKey(entry.getKey()), entry.getValue());
        }
      }
    }
    serverAttributesExtractor.onStart(attributes, parentContext, request);
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
    DbClientAttributesExtractor.onEndCommon(attributes, getter, request, response, error);
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
