/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.instrumentation.api.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class JedisDbAttributesExtractor extends DbAttributesExtractor<JedisRequest, Void> {
  @Override
  protected String system(JedisRequest request) {
    return SemanticAttributes.DbSystemValues.REDIS;
  }

  @Override
  @Nullable
  protected String user(JedisRequest request) {
    return null;
  }

  @Override
  protected String name(JedisRequest request) {
    return null;
  }

  @Override
  protected String connectionString(JedisRequest request) {
    return null;
  }

  @Override
  protected String statement(JedisRequest request) {
    return RedisCommandSanitizer.sanitize(request.getCommand().name(), request.getArgs());
  }

  @Override
  protected String operation(JedisRequest request) {
    return request.getCommand().name();
  }
}
