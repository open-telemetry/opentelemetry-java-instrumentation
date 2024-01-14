/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;

final class LettuceConnectNetworkAttributesGetter implements ServerAttributesGetter<RedisURI> {

  @Override
  public String getServerAddress(RedisURI redisUri) {
    return redisUri.getHost();
  }

  @Override
  public Integer getServerPort(RedisURI redisUri) {
    return redisUri.getPort();
  }
}
