/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetResponseAttributesExtractor;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

final class CassandraNetAttributesExtractor
    extends InetSocketAddressNetResponseAttributesExtractor<CassandraRequest, ExecutionInfo> {

  @Override
  @Nullable
  public String transport(@Nullable ExecutionInfo executionInfo) {
    return null;
  }

  @Override
  public @Nullable InetSocketAddress getAddress(@Nullable ExecutionInfo executionInfo) {
    if (executionInfo == null) {
      return null;
    }
    Node coordinator = executionInfo.getCoordinator();
    if (coordinator == null) {
      return null;
    }
    // resolve() returns an existing InetSocketAddress, it does not do a dns resolve,
    // at least in the only current EndPoint implementation (DefaultEndPoint)
    SocketAddress address = coordinator.getEndPoint().resolve();
    return address instanceof InetSocketAddress ? (InetSocketAddress) address : null;
  }
}
