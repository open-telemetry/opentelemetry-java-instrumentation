/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;

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
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/db/database-spans.md">database
 * client attributes</a>.
 *
 * <p>This class delegates to a type-specific {@link DbClientAttributesGetter} for individual
 * attribute extraction from request/response objects.
 */
public final class DbClientAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE>, SpanKeyProvider {

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_NAME = AttributeKey.stringKey("db.name");
  private static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system");
  private static final AttributeKey<String> DB_USER = AttributeKey.stringKey("db.user");
  private static final AttributeKey<String> DB_CONNECTION_STRING =
      AttributeKey.stringKey("db.connection_string");
  private static final AttributeKey<String> DB_STATEMENT = AttributeKey.stringKey("db.statement");
  private static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation");
  private static final AttributeKeyTemplate<String> DB_QUERY_PARAMETER =
      AttributeKeyTemplate.stringKeyTemplate("db.query.parameter");

  private final DbClientAttributesGetter<REQUEST, RESPONSE> getter;
  private final InternalNetworkAttributesExtractor<REQUEST, RESPONSE> internalNetworkExtractor;
  private final ServerAttributesExtractor<REQUEST, RESPONSE> serverAttributesExtractor;
  private final boolean captureQueryParameters;

  /** Creates the database client attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      DbClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return new DbClientAttributesExtractor<>(getter, false);
  }

  /**
   * Returns a new {@link DbClientAttributesExtractorBuilder} that can be used to configure the
   * database client attributes extractor.
   */
  public static <REQUEST, RESPONSE> DbClientAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      DbClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return new DbClientAttributesExtractorBuilder<>(getter);
  }

  DbClientAttributesExtractor(
      DbClientAttributesGetter<REQUEST, RESPONSE> getter, boolean captureQueryParameters) {
    this.getter = getter;
    this.captureQueryParameters = captureQueryParameters;
    internalNetworkExtractor = new InternalNetworkAttributesExtractor<>(getter, true, false);
    serverAttributesExtractor = ServerAttributesExtractor.create(getter);
  }

  @SuppressWarnings("deprecation") // until old db semconv are dropped
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    onStartCommon(attributes, getter, request, captureQueryParameters);
    serverAttributesExtractor.onStart(attributes, parentContext, request);
  }

  @SuppressWarnings("deprecation") // until old db semconv are dropped
  static <REQUEST, RESPONSE> void onStartCommon(
      AttributesBuilder attributes,
      DbClientAttributesGetter<REQUEST, RESPONSE> getter,
      REQUEST request,
      boolean captureQueryParameters) {
    Long batchSize = getter.getDbOperationBatchSize(request);
    boolean isBatch = batchSize != null && batchSize > 1;

    if (emitStableDatabaseSemconv()) {
      attributes.put(
          DB_SYSTEM_NAME, SemconvStability.stableDbSystemName(getter.getDbSystemName(request)));
      attributes.put(DB_NAMESPACE, getter.getDbNamespace(request));
      attributes.put(DB_QUERY_TEXT, getter.getDbQueryText(request));
      attributes.put(DB_OPERATION_NAME, getter.getDbOperationName(request));
      attributes.put(DB_QUERY_SUMMARY, getter.getDbQuerySummary(request));
      if (isBatch) {
        attributes.put(DB_OPERATION_BATCH_SIZE, batchSize);
      }
    }
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_SYSTEM, getter.getDbSystemName(request));
      attributes.put(DB_USER, getter.getUser(request));
      attributes.put(DB_NAME, getter.getDbNamespace(request));
      attributes.put(DB_CONNECTION_STRING, getter.getConnectionString(request));
      attributes.put(DB_STATEMENT, getter.getDbQueryText(request));
      attributes.put(DB_OPERATION, getter.getDbOperationName(request));
    }

    // Query parameters are an incubating feature and work with both old and new semconv
    if (captureQueryParameters && !isBatch) {
      Map<String, String> queryParameters = getter.getDbQueryParameters(request);
      if (queryParameters != null && !queryParameters.isEmpty()) {
        for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
          String key = entry.getKey();
          String value = entry.getValue();
          attributes.put(DB_QUERY_PARAMETER.getAttributeKey(key), value);
        }
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
    internalNetworkExtractor.onEnd(attributes, request, response);
    onEndCommon(attributes, getter, request, response, error);
  }

  static <REQUEST, RESPONSE> void onEndCommon(
      AttributesBuilder attributes,
      DbClientAttributesGetter<REQUEST, RESPONSE> getter,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    if (emitStableDatabaseSemconv()) {
      String errorType = getter.getErrorType(request, response, error);
      // fall back to exception class name
      if (errorType == null && error != null) {
        errorType = error.getClass().getName();
      }
      attributes.put(ERROR_TYPE, errorType);
    }
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
