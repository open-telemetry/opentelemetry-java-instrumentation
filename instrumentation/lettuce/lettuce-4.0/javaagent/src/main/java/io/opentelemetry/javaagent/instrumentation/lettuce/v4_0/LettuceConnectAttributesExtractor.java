/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_REDIS_DATABASE_INDEX;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

final class LettuceConnectAttributesExtractor implements AttributesExtractor<RedisURI, Void> {

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, RedisURI redisUri) {
    if (emitStableDatabaseSemconv()) {
      attributes.put(DB_SYSTEM_NAME, DbSystemNameIncubatingValues.REDIS);
    }
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_SYSTEM, DbSystemIncubatingValues.REDIS);
    }

    int database = redisUri.getDatabase();
    if (database != 0) {
      if (emitStableDatabaseSemconv()) {
        attributes.put(DB_NAMESPACE, String.valueOf(database));
      }
      if (emitOldDatabaseSemconv()) {
        attributes.put(DB_REDIS_DATABASE_INDEX, (long) database);
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      RedisURI redisUri,
      Void unused,
      @Nullable Throwable error) {}
}
