/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisURI;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class LettuceConnectAttributesExtractor implements AttributesExtractor<RedisURI, Void> {

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, RedisURI redisUri) {
    attributes.put(DbIncubatingAttributes.DB_SYSTEM, DbIncubatingAttributes.DbSystemValues.REDIS);

    int database = redisUri.getDatabase();
    if (database != 0) {
      if (SemconvStability.emitStableDatabaseSemconv()) {
        attributes.put(
            AttributeKey.stringKey("db.namespace"),
            redisUri.getHost()); // TODO (heya) required further discussion
      }

      if (SemconvStability.emitOldDatabaseSemconv()) {
        attributes.put(DbIncubatingAttributes.DB_REDIS_DATABASE_INDEX, (long) database);
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
