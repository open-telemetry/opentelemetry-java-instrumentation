/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.protocol.internal.Frame;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public class CassandraResponsePeers {

  private static final VirtualField<Frame, SocketAddress> FRAME_PEER =
      VirtualField.find(Frame.class, SocketAddress.class);

  private static final VirtualField<ExecutionInfo, SocketAddress> EXECUTION_INFO_PEER =
      VirtualField.find(ExecutionInfo.class, SocketAddress.class);

  public static void setFramePeer(Frame frame, SocketAddress peer) {
    FRAME_PEER.set(frame, peer);
  }

  @Nullable
  public static SocketAddress getFramePeer(Frame frame) {
    return FRAME_PEER.get(frame);
  }

  public static void setExecutionInfoPeer(ExecutionInfo executionInfo, SocketAddress peer) {
    EXECUTION_INFO_PEER.set(executionInfo, peer);
  }

  @Nullable
  public static SocketAddress getExecutionInfoPeer(ExecutionInfo executionInfo) {
    return EXECUTION_INFO_PEER.get(executionInfo);
  }

  private CassandraResponsePeers() {}
}
