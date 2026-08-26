/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.hadoop.hbase.ipc;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

// Helper for accessing the virtual field on package-private Call.
public final class OpenTelemetryCallUtil {
  private static final VirtualField<Call, RequestAndContext> REQUEST_AND_CONTEXT =
      VirtualField.find(Call.class, RequestAndContext.class);

  public static void setRequestAndContext(
      Object call, @Nullable RequestAndContext requestAndContext) {
    synchronized (call) {
      REQUEST_AND_CONTEXT.set((Call) call, requestAndContext);
    }
  }

  public static boolean isCall(Object message) {
    return message instanceof Call;
  }

  public static void setNetworkPeer(Object message, @Nullable SocketAddress remoteAddress) {
    if (!(message instanceof Call) || !(remoteAddress instanceof InetSocketAddress)) {
      return;
    }

    InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;
    InetAddress inetAddress = inetSocketAddress.getAddress();
    if (inetAddress == null) {
      return;
    }

    Call call = (Call) message;
    synchronized (call) {
      RequestAndContext requestAndContext = REQUEST_AND_CONTEXT.get(call);
      if (requestAndContext != null) {
        REQUEST_AND_CONTEXT.set(
            call,
            requestAndContext.withNetworkPeer(
                inetAddress.getHostAddress(), inetSocketAddress.getPort()));
      }
    }
  }

  @Nullable
  public static RequestAndContext getAndClearRequestAndContext(Object call) {
    synchronized (call) {
      RequestAndContext requestAndContext = REQUEST_AND_CONTEXT.get((Call) call);
      REQUEST_AND_CONTEXT.set((Call) call, null);
      return requestAndContext;
    }
  }

  private OpenTelemetryCallUtil() {}
}
