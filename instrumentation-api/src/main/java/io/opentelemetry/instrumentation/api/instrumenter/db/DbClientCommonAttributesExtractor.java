/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

abstract class DbClientCommonAttributesExtractor<
        REQUEST, RESPONSE, GETTER extends DbClientCommonAttributesGetter<REQUEST>>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  final GETTER getter;

  DbClientCommonAttributesExtractor(GETTER getter) {
    this.getter = getter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    set(attributes, SemanticAttributes.DB_SYSTEM, getter.system(request));
    set(attributes, SemanticAttributes.DB_USER, getter.user(request));
    set(attributes, SemanticAttributes.DB_NAME, getter.name(request));
    set(attributes, SemanticAttributes.DB_CONNECTION_STRING, getter.connectionString(request));
  }

  @Override
  public final void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
