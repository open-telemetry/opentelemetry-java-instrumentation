/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class LettuceDbAttributesExtractor
    implements AttributesExtractor<LettuceRequest, LettuceResponse> {

  // copied from DbIncubatingAttributes
  private static final AttributeKey<Long> DB_REDIS_DATABASE_INDEX =
      AttributeKey.longKey("db.redis.database_index");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, LettuceRequest request) {
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_REDIS_DATABASE_INDEX, request.getDatabaseIndex());
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      LettuceRequest request,
      @Nullable LettuceResponse response,
      @Nullable Throwable error) {}
}
