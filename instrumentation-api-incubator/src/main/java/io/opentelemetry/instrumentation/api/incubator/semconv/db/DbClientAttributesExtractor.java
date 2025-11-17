/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;

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

  private final DbClientAttributesGetter<REQUEST, RESPONSE> getter;

  /** Creates the database client attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      DbClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return new DbClientAttributesExtractor<>(getter);
  }

  DbClientAttributesExtractor(DbClientAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  @SuppressWarnings("deprecation") // until old db semconv are dropped
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    onStartCommon(attributes, getter, request);
  }

  @SuppressWarnings("deprecation") // until old db semconv are dropped
  static <REQUEST, RESPONSE> void onStartCommon(
      AttributesBuilder attributes,
      DbClientAttributesGetter<REQUEST, RESPONSE> getter,
      REQUEST request) {
    if (SemconvStability.emitStableDatabaseSemconv()) {
      internalSet(
          attributes,
          DB_SYSTEM_NAME,
          SemconvStability.stableDbSystemName(getter.getDbSystem(request)));
      internalSet(attributes, DB_NAMESPACE, getter.getDbNamespace(request));
      internalSet(attributes, DB_QUERY_TEXT, getter.getDbQueryText(request));
      internalSet(attributes, DB_OPERATION_NAME, getter.getDbOperationName(request));
      internalSet(attributes, DB_QUERY_SUMMARY, getter.getDbQuerySummary(request));
    }
    if (SemconvStability.emitOldDatabaseSemconv()) {
      internalSet(attributes, DB_SYSTEM, getter.getDbSystem(request));
      internalSet(attributes, DB_USER, getter.getUser(request));
      internalSet(attributes, DB_NAME, getter.getDbNamespace(request));
      internalSet(attributes, DB_CONNECTION_STRING, getter.getConnectionString(request));
      internalSet(attributes, DB_STATEMENT, getter.getDbQueryText(request));
      internalSet(attributes, DB_OPERATION, getter.getDbOperationName(request));
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    onEndCommon(attributes, getter, response, error);
  }

  static <REQUEST, RESPONSE> void onEndCommon(
      AttributesBuilder attributes,
      DbClientAttributesGetter<REQUEST, RESPONSE> getter,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    if (SemconvStability.emitStableDatabaseSemconv()) {
      if (error != null) {
        internalSet(attributes, ERROR_TYPE, error.getClass().getName());
      }
      if (error != null || response != null) {
        internalSet(attributes, DB_RESPONSE_STATUS_CODE, getter.getResponseStatus(response, error));
      }
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
