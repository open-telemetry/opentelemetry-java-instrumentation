/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import com.google.auto.value.AutoValue;
import org.redisson.client.RedisConnection;

@AutoValue
public abstract class RedissonRequest {

  public abstract RedisConnection getConnection();

  public abstract Object getCommand();

  public static RedissonRequest create(RedisConnection connection, Object command) {
    return new AutoValue_RedissonRequest(connection, command);
  }
}
