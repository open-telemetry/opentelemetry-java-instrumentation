/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisURI;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class LettuceConnectAttributesExtractor implements AttributesExtractor<RedisURI, Void> {

  @Override
  public void onStart(AttributesBuilder attributes, RedisURI redisUri) {
    attributes.put(SemanticAttributes.DB_SYSTEM, SemanticAttributes.DbSystemValues.REDIS);

    int database = redisUri.getDatabase();
    if (database != 0) {
      attributes.put(SemanticAttributes.DB_REDIS_DATABASE_INDEX, (long) database);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes, RedisURI redisUri, Void unused, @Nullable Throwable error) {}
}
