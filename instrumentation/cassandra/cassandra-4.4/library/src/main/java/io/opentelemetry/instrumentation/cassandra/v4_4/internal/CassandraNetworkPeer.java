/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4.internal;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * Bridges response peer data from the javaagent to library instrumentation.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class CassandraNetworkPeer {

  private static final VirtualField<ExecutionInfo, SocketAddress> EXECUTION_INFO_PEER =
      VirtualField.find(ExecutionInfo.class, SocketAddress.class);

  public static void setExecutionInfoPeer(ExecutionInfo executionInfo, SocketAddress peer) {
    EXECUTION_INFO_PEER.set(executionInfo, peer);
  }

  @Nullable
  public static SocketAddress getExecutionInfoPeer(ExecutionInfo executionInfo) {
    return EXECUTION_INFO_PEER.get(executionInfo);
  }

  private CassandraNetworkPeer() {}
}
