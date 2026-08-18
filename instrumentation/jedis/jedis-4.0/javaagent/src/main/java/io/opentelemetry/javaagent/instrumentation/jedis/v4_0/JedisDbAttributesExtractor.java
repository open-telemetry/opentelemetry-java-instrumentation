/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_REDIS_DATABASE_INDEX;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class JedisDbAttributesExtractor implements AttributesExtractor<JedisRequest, Void> {

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, JedisRequest request) {
    if (emitOldDatabaseSemconv()) {
      Long databaseIndex = request.getDatabaseIndex();
      // the old semantic conventions capture the database index only when it is not the default 0
      if (databaseIndex != null && databaseIndex != 0) {
        attributes.put(DB_REDIS_DATABASE_INDEX, databaseIndex);
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      JedisRequest request,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
