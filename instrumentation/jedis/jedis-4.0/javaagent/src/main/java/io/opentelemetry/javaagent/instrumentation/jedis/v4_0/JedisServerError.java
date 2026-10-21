/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisErrorPrefix;
import javax.annotation.Nullable;

public final class JedisServerError {
  @Nullable
  public static String errorType(@Nullable String reply) {
    return RedisErrorPrefix.extract(reply);
  }

  private JedisServerError() {}
}
