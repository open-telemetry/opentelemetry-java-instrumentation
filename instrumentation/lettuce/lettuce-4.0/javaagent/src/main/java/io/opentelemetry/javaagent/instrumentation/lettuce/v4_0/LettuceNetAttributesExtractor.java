/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

final class LettuceNetAttributesExtractor extends NetAttributesExtractor<RedisURI, Void> {

  @Override
  @Nullable
  protected String transport(RedisURI redisUri) {
    return null;
  }

  @Override
  protected String peerName(RedisURI redisUri, @Nullable Void ignored) {
    return redisUri.getHost();
  }

  @Override
  protected Integer peerPort(RedisURI redisUri, @Nullable Void ignored) {
    return redisUri.getPort();
  }

  @Override
  @Nullable
  protected String peerIp(RedisURI redisUri, @Nullable Void ignored) {
    return null;
  }
}
