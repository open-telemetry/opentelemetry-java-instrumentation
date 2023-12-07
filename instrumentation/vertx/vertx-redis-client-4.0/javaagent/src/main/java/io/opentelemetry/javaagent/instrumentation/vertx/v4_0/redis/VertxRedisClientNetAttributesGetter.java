/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.redis;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import javax.annotation.Nullable;

enum VertxRedisClientNetAttributesGetter
    implements
        ServerAttributesGetter<VertxRedisClientRequest>,
        NetworkAttributesGetter<VertxRedisClientRequest, Void> {
  INSTANCE;

  @Nullable
  @Override
  public String getServerAddress(VertxRedisClientRequest request) {
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(VertxRedisClientRequest request) {
    return request.getPort();
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(VertxRedisClientRequest request, @Nullable Void unused) {
    return request.getPeerAddress();
  }

  @Override
  @Nullable
  public Integer getNetworkPeerPort(VertxRedisClientRequest request, @Nullable Void unused) {
    return request.getPeerPort();
  }
}
