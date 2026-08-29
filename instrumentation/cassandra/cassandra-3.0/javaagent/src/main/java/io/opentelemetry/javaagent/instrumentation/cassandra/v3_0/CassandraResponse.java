/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.exceptions.CoordinatorException;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

class CassandraResponse {
  @Nullable private final ExecutionInfo executionInfo;
  @Nullable private final InetSocketAddress peerAddress;

  private CassandraResponse(
      @Nullable ExecutionInfo executionInfo, @Nullable InetSocketAddress peerAddress) {
    this.executionInfo = executionInfo;
    this.peerAddress = peerAddress;
  }

  static CassandraResponse create(ExecutionInfo executionInfo) {
    Host coordinator = executionInfo.getQueriedHost();
    if (coordinator == null) {
      return new CassandraResponse(executionInfo, null);
    }
    if (emitStableDatabaseSemconv() && CassandraEndPoints.isSniEndPoint(coordinator)) {
      // SniEndPoint.resolve() returns the proxy, performs DNS, and advances the driver's shared
      // round-robin counter, so the actual proxy is not safe to obtain here.
      return new CassandraResponse(executionInfo, null);
    }
    InetSocketAddress address = coordinator.getSocketAddress();
    return new CassandraResponse(executionInfo, address);
  }

  @Nullable
  static CassandraResponse create(Throwable throwable) {
    if (!(throwable instanceof CoordinatorException)) {
      return null;
    }
    CoordinatorException exception = (CoordinatorException) throwable;
    if (emitStableDatabaseSemconv() && CassandraEndPoints.isSniEndPoint(exception)) {
      return new CassandraResponse(null, null);
    }
    InetSocketAddress address = exception.getAddress();
    return new CassandraResponse(null, address);
  }

  @Nullable
  ExecutionInfo getExecutionInfo() {
    return executionInfo;
  }

  @Nullable
  InetSocketAddress getPeerAddress() {
    return peerAddress;
  }
}
