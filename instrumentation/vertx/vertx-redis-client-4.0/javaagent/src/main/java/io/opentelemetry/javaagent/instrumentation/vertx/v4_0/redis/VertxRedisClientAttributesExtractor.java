/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.redis;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

enum VertxRedisClientAttributesExtractor
    implements AttributesExtractor<VertxRedisClientRequest, Void> {
  INSTANCE;

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, VertxRedisClientRequest request) {
    internalSet(
        attributes, DbIncubatingAttributes.DB_REDIS_DATABASE_INDEX, request.getDatabaseIndex());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      VertxRedisClientRequest request,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
