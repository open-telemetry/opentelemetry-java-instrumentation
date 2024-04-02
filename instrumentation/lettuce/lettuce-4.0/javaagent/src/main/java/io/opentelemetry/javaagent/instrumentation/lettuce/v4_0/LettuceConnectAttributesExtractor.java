/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class LettuceConnectAttributesExtractor implements AttributesExtractor<RedisURI, Void> {

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, RedisURI redisUri) {
    attributes.put(DbIncubatingAttributes.DB_SYSTEM, DbIncubatingAttributes.DbSystemValues.REDIS);

    int database = redisUri.getDatabase();
    if (database != 0) {
      attributes.put(DbIncubatingAttributes.DB_REDIS_DATABASE_INDEX, (long) database);
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
