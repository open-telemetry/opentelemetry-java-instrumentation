/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;

final class LettuceConnectNetAttributesGetter implements NetClientAttributesGetter<RedisURI, Void> {

  @Override
  public String getPeerName(RedisURI redisUri) {
    return redisUri.getHost();
  }

  @Override
  public Integer getPeerPort(RedisURI redisUri) {
    return redisUri.getPort();
  }
}
