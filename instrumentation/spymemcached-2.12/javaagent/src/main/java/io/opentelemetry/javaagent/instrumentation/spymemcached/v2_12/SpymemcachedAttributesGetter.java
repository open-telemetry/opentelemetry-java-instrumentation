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
import net.spy.memcached.internal.BulkFuture;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;
import net.spy.memcached.ops.OperationStatus;
import net.spy.memcached.ops.StatusCode;

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
  public String getDbQueryText(SpymemcachedRequest spymemcachedRequest) {
    return spymemcachedRequest.getQueryText();
  }

  @Override
  public String getDbOperationName(SpymemcachedRequest spymemcachedRequest) {
    return spymemcachedRequest.getStableOperationName();
  }

  @Override
  @Nullable
  public String getDbResponseStatusCode(
      SpymemcachedRequest request, @Nullable Object response, @Nullable Throwable error) {
    OperationStatus status = getOperationStatus(response);
    if (status == null) {
      return null;
    }
    StatusCode code = status.getStatusCode();
    if (code == null) {
      return null;
    }
    return code.name();
  }

  @Override
  @Nullable
  public String getErrorType(
      SpymemcachedRequest request, @Nullable Object response, @Nullable Throwable error) {
    if (error != null) {
      return DbClientAttributesGetter.super.getErrorType(request, response, error);
    }
    OperationStatus status = getOperationStatus(response, error);
    if (status == null || status.isSuccess()) {
      return null;
    }
    StatusCode code = status.getStatusCode();
    if (code == null) {
      return null;
    }
    switch (code) {
      case SUCCESS:
      case ERR_NOT_FOUND:
      case ERR_EXISTS:
      case ERR_NOT_STORED:
      case CANCELLED:
        return null;
      default:
        return code.name();
    }
  }

  @Nullable
  private static OperationStatus getOperationStatus(
      @Nullable Object response, @Nullable Throwable error) {
    if (error != null || !(response instanceof OperationFuture)) {
      return null;
    }
    OperationFuture<?> future = (OperationFuture<?>) response;
    if (future.isCancelled()) {
      return null;
    }
    return future.getStatus();
  }

  @Nullable
  private static OperationStatus getOperationStatus(@Nullable Object response) {
    if (response instanceof OperationFuture) {
      return ((OperationFuture<?>) response).getStatus();
    }
    if (response instanceof GetFuture) {
      return ((GetFuture<?>) response).getStatus();
    }
    if (response instanceof BulkFuture) {
      return ((BulkFuture<?>) response).getStatus();
    }
    return null;
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
