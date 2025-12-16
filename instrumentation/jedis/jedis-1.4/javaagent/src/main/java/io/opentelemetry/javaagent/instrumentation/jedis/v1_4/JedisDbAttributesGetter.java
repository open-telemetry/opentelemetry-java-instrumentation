/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;

final class JedisDbAttributesGetter implements DbClientAttributesGetter<JedisRequest, Void> {

  private final RedisCommandSanitizer sanitizer;

  JedisDbAttributesGetter(boolean statementSanitizerEnabled) {
    this.sanitizer = RedisCommandSanitizer.create(statementSanitizerEnabled);
  }

  @Override
  public String getDbSystem(JedisRequest request) {
    return DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  public String getDbNamespace(JedisRequest request) {
    return null;
  }

  @Override
  public String getDbQueryText(JedisRequest request) {
    return sanitizer.sanitize(request.getCommand().name(), request.getArgs());
  }

  @Override
  public String getDbOperationName(JedisRequest request) {
    return request.getCommand().name();
  }
}
