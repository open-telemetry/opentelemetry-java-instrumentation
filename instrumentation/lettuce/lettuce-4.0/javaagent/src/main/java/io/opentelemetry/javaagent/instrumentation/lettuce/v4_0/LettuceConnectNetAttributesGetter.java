/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;

final class LettuceConnectNetAttributesGetter implements NetClientAttributesGetter<RedisURI, Void> {

  @Override
  @Nullable
  public String transport(RedisURI redisUri, @Nullable Void unused) {
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
}
