/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class RedissonDbAttributesGetter implements DbClientAttributesGetter<RedissonRequest, Void> {

  @SuppressWarnings("deprecation") // using deprecated DbSystemIncubatingValues
  @Override
  public String getDbSystem(RedissonRequest request) {
    return DbIncubatingAttributes.DbSystemIncubatingValues.REDIS;
  }

  @Nullable
  @Override
  public String getDbNamespace(RedissonRequest request) {
    return null;
  }

  @Deprecated
  @Override
  public String getDbQueryText(RedissonRequest request) {
    return request.getStatement();
  }

  @Nullable
  @Override
  public String getDbOperationName(RedissonRequest request) {
    return request.getOperation();
  }

  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      RedissonRequest request, @Nullable Void unused) {
    return request.getAddress();
  }
}
