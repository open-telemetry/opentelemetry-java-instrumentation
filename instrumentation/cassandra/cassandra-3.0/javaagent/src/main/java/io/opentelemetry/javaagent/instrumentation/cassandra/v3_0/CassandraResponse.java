/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.exceptions.CoordinatorException;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

class CassandraResponse {
  @Nullable private final ExecutionInfo executionInfo;
  @Nullable private final InetSocketAddress coordinatorAddress;

  private CassandraResponse(
      @Nullable ExecutionInfo executionInfo, @Nullable InetSocketAddress coordinatorAddress) {
    this.executionInfo = executionInfo;
    this.coordinatorAddress = coordinatorAddress;
  }

  static CassandraResponse create(ExecutionInfo executionInfo) {
    Host coordinator = executionInfo.getQueriedHost();
    return new CassandraResponse(
        executionInfo, coordinator == null ? null : coordinator.getSocketAddress());
  }

  @Nullable
  static CassandraResponse create(Throwable throwable) {
    if (throwable instanceof CoordinatorException) {
      return new CassandraResponse(null, ((CoordinatorException) throwable).getAddress());
    }
    return null;
  }

  @Nullable
  ExecutionInfo getExecutionInfo() {
    return executionInfo;
  }

  @Nullable
  InetSocketAddress getCoordinatorAddress() {
    return coordinatorAddress;
  }
}
