/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;

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

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      VertxSqlClientRequest request,
      @Nullable Void response,
      @Nullable Throwable error) {
    if (!(request instanceof VertxSqlClientDeferredRequest)
        || !((VertxSqlClientDeferredRequest) request).isInfoUpdated()) {
      return;
    }
    attributes.put(DB_NAMESPACE, getter.getDbNamespace(request));
    serverAttributesExtractor.onStart(attributes, context, request);
  }
}
