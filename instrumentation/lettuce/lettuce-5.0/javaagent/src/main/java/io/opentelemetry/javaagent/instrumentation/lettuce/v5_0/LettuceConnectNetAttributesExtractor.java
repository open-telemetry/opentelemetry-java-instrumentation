/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisURI;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesAdapter;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import javax.annotation.Nullable;

final class LettuceConnectNetAttributesExtractor
    implements NetAttributesAdapter<RedisURI, Void> {

  @Override
  @Nullable
  public String transport(RedisURI redisUri, @Nullable Void unused) {
    return null;
  }

  @Override
  public String peerName(RedisURI redisUri, @Nullable Void unused) {
    return redisUri.getHost();
  }

  @Override
  public Integer peerPort(RedisURI redisUri, @Nullable Void unused) {
    return redisUri.getPort();
  }

  @Override
  @Nullable
  public String peerIp(RedisURI redisUri, @Nullable Void unused) {
    return null;
  }
}
