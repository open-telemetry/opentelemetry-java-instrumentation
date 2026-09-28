/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

class SpymemcachedAttributesGetter
    implements DbClientAttributesGetter<SpymemcachedRequest, Object> {

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
  @SuppressWarnings("deprecation") // old database semconv still use db.operation
  public String getDbOperation(SpymemcachedRequest spymemcachedRequest) {
    return spymemcachedRequest.getOperationName();
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      SpymemcachedRequest spymemcachedRequest, @Nullable Object response) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    InetSocketAddress address = spymemcachedRequest.getHandlingNodeAddress();
    return address == null || address.isUnresolved() ? null : address;
  }

  @Override
  @Nullable
  public String getServerAddress(SpymemcachedRequest spymemcachedRequest) {
    if (emitStableDatabaseSemconv()) {
      DbServerTarget target = spymemcachedRequest.getServerTarget();
      return target == null ? null : target.getAddress();
    }
    InetSocketAddress address = spymemcachedRequest.getHandlingNodeAddress();
    return address == null ? null : address.getHostString();
  }

  @Override
  @Nullable
  public Integer getServerPort(SpymemcachedRequest spymemcachedRequest) {
    if (emitStableDatabaseSemconv()) {
      DbServerTarget target = spymemcachedRequest.getServerTarget();
      return target == null ? null : target.getPort();
    }
    InetSocketAddress address = spymemcachedRequest.getHandlingNodeAddress();
    return address == null ? null : address.getPort();
  }
}
