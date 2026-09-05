/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import javax.annotation.Nullable;

class VertxSqlClientLateAttributesExtractor
    implements AttributesExtractor<VertxSqlClientRequest, Void> {

  private final VertxSqlClientAttributesGetter getter;
  private final AttributesExtractor<VertxSqlClientRequest, Void> serverAttributesExtractor;

  VertxSqlClientLateAttributesExtractor(VertxSqlClientAttributesGetter getter) {
    this.getter = getter;
    serverAttributesExtractor = ServerAttributesExtractor.create(getter);
  }

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, VertxSqlClientRequest request) {}

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      VertxSqlClientRequest request,
      @Nullable Void response,
      @Nullable Throwable error) {
    if (!request.isInfoUpdated()) {
      return;
    }
    if (emitStableDatabaseSemconv()) {
      attributes.put(DB_NAMESPACE, getter.getDbNamespace(request));
    }
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_USER, getter.getUser(request));
      attributes.put(DB_NAME, getter.getDbName(request));
    }
    serverAttributesExtractor.onStart(attributes, context, request);
  }
}
