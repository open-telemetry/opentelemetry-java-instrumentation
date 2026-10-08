/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

class SpymemcachedAttributesGetter
    implements DbClientAttributesGetter<SpymemcachedRequest, Object> {

  @Override
  @Nullable
  public String getErrorType(
      SpymemcachedRequest request, @Nullable Object response, @Nullable Throwable error) {
    return null;
  }

  @Override
  public String getDbSystemName(SpymemcachedRequest spymemcachedRequest) {
    return DbSystemNameIncubatingValues.MEMCACHED;
  }

  @Override
  @Nullable
  public String getDbNamespace(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(SpymemcachedRequest spymemcachedRequest) {
    return null;
  }

  @Override
  public String getDbOperationName(SpymemcachedRequest spymemcachedRequest) {
    return spymemcachedRequest.getStableOperationName();
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      SpymemcachedRequest spymemcachedRequest, @Nullable Object response) {
    InetSocketAddress address = spymemcachedRequest.getHandlingNodeAddress();
    return address == null || address.isUnresolved() ? null : address;
  }

  @Override
  @Nullable
  public String getServerAddress(SpymemcachedRequest spymemcachedRequest) {
    DbServerTarget target = spymemcachedRequest.getServerTarget();
    return target == null ? null : target.getAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(SpymemcachedRequest spymemcachedRequest) {
    DbServerTarget target = spymemcachedRequest.getServerTarget();
    return target == null ? null : target.getPort();
  }
}
