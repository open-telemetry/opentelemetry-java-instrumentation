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
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import javax.annotation.Nullable;

abstract class DbClientCommonAttributesExtractor<
        REQUEST, RESPONSE, GETTER extends DbClientCommonAttributesGetter<REQUEST>>
    implements AttributesExtractor<REQUEST, RESPONSE>, SpanKeyProvider {

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_NAME = AttributeKey.stringKey("db.name");
  private static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system");
  private static final AttributeKey<String> DB_USER = AttributeKey.stringKey("db.user");
  private static final AttributeKey<String> DB_CONNECTION_STRING =
      AttributeKey.stringKey("db.connection_string");

  final GETTER getter;

  DbClientCommonAttributesExtractor(GETTER getter) {
    this.getter = getter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalSet(attributes, DB_SYSTEM, getter.getSystem(request));
    internalSet(attributes, DB_USER, getter.getUser(request));
    internalSet(attributes, DB_NAME, getter.getName(request));
    internalSet(attributes, DB_CONNECTION_STRING, getter.getConnectionString(request));
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
  @Override
  public SpanKey internalGetSpanKey() {
    return SpanKey.DB_CLIENT;
  }
}
