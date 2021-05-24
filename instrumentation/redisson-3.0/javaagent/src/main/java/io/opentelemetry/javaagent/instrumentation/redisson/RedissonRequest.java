/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import com.google.auto.value.AutoValue;
import java.net.InetSocketAddress;

@AutoValue
public abstract class RedissonRequest {

  public abstract InetSocketAddress getAddress();

  public abstract Object getCommand();

  public static RedissonRequest create(InetSocketAddress address, Object command) {
    return new AutoValue_RedissonRequest(address, command);
  }
}
