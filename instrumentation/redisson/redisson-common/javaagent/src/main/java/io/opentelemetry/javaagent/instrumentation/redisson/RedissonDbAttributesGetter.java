/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class RedissonDbAttributesGetter implements DbClientAttributesGetter<RedissonRequest, Void> {

  @Override
  public String getDbSystemName(RedissonRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Nullable
  @Override
  public String getDbNamespace(RedissonRequest request) {
    return null;
  }

  @Override
  public String getDbQueryText(RedissonRequest request) {
    return request.getQueryText();
  }

  @Nullable
  @Override
  public String getDbOperationName(RedissonRequest request) {
    return request.getOperationName();
  }

  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      RedissonRequest request, @Nullable Void unused) {
    return request.getAddress();
  }
}
