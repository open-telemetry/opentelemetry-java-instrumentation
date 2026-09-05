/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class RedissonBatchDbAttributesGetter
    implements DbClientAttributesGetter<RedissonBatchRequest, Void> {

  @Override
  public String getDbSystemName(RedissonBatchRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Nullable
  @Override
  public String getDbNamespace(RedissonBatchRequest request) {
    return null;
  }

  @Override
  public String getDbQueryText(RedissonBatchRequest request) {
    return request.getQueryText();
  }

  @Override
  public String getDbOperationName(RedissonBatchRequest request) {
    return request.getOperationName();
  }

  @Nullable
  @Override
  public Long getDbOperationBatchSize(RedissonBatchRequest request) {
    return request.getOperationBatchSize();
  }

  @Nullable
  @Override
  public String getServerAddress(RedissonBatchRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(RedissonBatchRequest request) {
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      RedissonBatchRequest request, @Nullable Void unused) {
    // A batch may target multiple nodes, and no reliable endpoint is known before execution.
    return null;
  }
}
