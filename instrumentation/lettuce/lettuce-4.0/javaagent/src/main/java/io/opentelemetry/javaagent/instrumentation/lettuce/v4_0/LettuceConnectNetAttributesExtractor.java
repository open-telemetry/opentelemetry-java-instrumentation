/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

final class LettuceConnectNetAttributesExtractor extends NetAttributesExtractor<RedisURI, Void> {

  @Override
  @Nullable
  public String transport(RedisURI redisUri) {
    return null;
  }

  @Override
  public String peerName(RedisURI redisUri) {
    return redisUri.getHost();
  }

  @Override
  public Integer peerPort(RedisURI redisUri) {
    return redisUri.getPort();
  }

  @Override
  @Nullable
  public String peerIp(RedisURI redisUri) {
    return null;
  }
}
