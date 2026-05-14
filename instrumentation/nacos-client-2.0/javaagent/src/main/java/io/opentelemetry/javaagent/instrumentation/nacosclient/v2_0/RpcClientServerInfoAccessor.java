/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacosclient.v2_0;

import com.alibaba.nacos.common.remote.client.RpcClient;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;

public final class RpcClientServerInfoAccessor {

  private static final VirtualField<RpcClient, RpcClient.ServerInfo> SERVER_INFO =
      VirtualField.find(RpcClient.class, RpcClient.ServerInfo.class);

  private RpcClientServerInfoAccessor() {}

  public static void set(RpcClient rpcClient, RpcClient.ServerInfo serverInfo) {
    SERVER_INFO.set(rpcClient, serverInfo);
  }

  @Nullable
  public static RpcClient.ServerInfo get(RpcClient rpcClient) {
    return SERVER_INFO.get(rpcClient);
  }

  public static String resolvePeer(RpcClient rpcClient) {
    RpcClient.ServerInfo serverInfo = get(rpcClient);
    if (serverInfo == null) {
      serverInfo = rpcClient.getCurrentServer();
    }
    return serverInfo != null ? serverInfo.getAddress() : "unknown";
  }
}
