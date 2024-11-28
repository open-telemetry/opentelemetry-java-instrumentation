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
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.InternalNetworkAttributesExtractor;
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
  private static final AttributeKey<String> DB_NAMESPACE = AttributeKey.stringKey("db.namespace");
  private static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system");
  private static final AttributeKey<String> DB_USER = AttributeKey.stringKey("db.user");
  private static final AttributeKey<String> DB_CONNECTION_STRING =
      AttributeKey.stringKey("db.connection_string");
  private static final AttributeKey<String> DB_STATEMENT = AttributeKey.stringKey("db.statement");
  private static final AttributeKey<String> DB_QUERY_TEXT = AttributeKey.stringKey("db.query.text");

  private static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation");
  private static final AttributeKey<String> DB_OPERATION_NAME =
      AttributeKey.stringKey("db.operation.name");

  private final DbClientAttributesGetter<REQUEST, RESPONSE> getter;
  private final InternalNetworkAttributesExtractor<REQUEST, RESPONSE> internalNetworkExtractor;
  private final ServerAttributesExtractor<REQUEST, RESPONSE> serverAttributesExtractor;

  /** Creates the database client attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      DbClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return new DbClientAttributesExtractor<>(getter);
  }

  DbClientAttributesExtractor(DbClientAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
    internalNetworkExtractor =
        new InternalNetworkAttributesExtractor<>(
            getter, SemconvStability.emitOldDatabaseSemconv(), false);
    serverAttributesExtractor = ServerAttributesExtractor.create(getter);
  }

  @Override
  @SuppressWarnings("deprecation") // using deprecated semconv
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalSet(attributes, DB_SYSTEM, getter.getDbSystem(request));
    if (SemconvStability.emitStableDatabaseSemconv()) {
      internalSet(attributes, DB_NAMESPACE, getter.getDbNamespace(request));
      internalSet(attributes, DB_QUERY_TEXT, getter.getDbQueryText(request));
      internalSet(attributes, DB_OPERATION_NAME, getter.getDbOperationName(request));
    }
    if (SemconvStability.emitOldDatabaseSemconv()) {
      internalSet(attributes, DB_USER, getter.getUser(request));
      internalSet(attributes, DB_NAME, getter.getDbNamespace(request));
      internalSet(attributes, DB_CONNECTION_STRING, getter.getConnectionString(request));
      internalSet(attributes, DB_STATEMENT, getter.getDbQueryText(request));
      internalSet(attributes, DB_OPERATION, getter.getDbOperationName(request));
    }
    serverAttributesExtractor.onStart(attributes, parentContext, request);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    internalNetworkExtractor.onEnd(attributes, request, response);
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
