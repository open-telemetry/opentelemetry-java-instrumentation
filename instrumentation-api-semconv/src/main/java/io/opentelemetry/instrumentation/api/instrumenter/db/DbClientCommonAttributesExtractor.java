/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.annotations.UnstableApi;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

abstract class DbClientCommonAttributesExtractor<
        REQUEST, RESPONSE, GETTER extends DbClientCommonAttributesGetter<REQUEST>>
    implements AttributesExtractor<REQUEST, RESPONSE>, SpanKeyProvider {

  final GETTER getter;

  DbClientCommonAttributesExtractor(GETTER getter) {
    this.getter = getter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalSet(attributes, SemanticAttributes.DB_SYSTEM, getter.system(request));
    internalSet(attributes, SemanticAttributes.DB_USER, getter.user(request));
    internalSet(attributes, SemanticAttributes.DB_NAME, getter.name(request));
    internalSet(
        attributes, SemanticAttributes.DB_CONNECTION_STRING, getter.connectionString(request));
  }

  @Override
  public final void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}

  /**
   * This method is internal and is hence not for public use. Its API is unstable and can change at
   * any time.
   */
  @UnstableApi
  @Override
  public SpanKey internalGetSpanKey() {
    return SpanKey.DB_CLIENT;
  }
}
