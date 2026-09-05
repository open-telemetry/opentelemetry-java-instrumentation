/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.hadoop.hbase.ipc;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

// Helper for accessing the package-private Call class.
public final class OpenTelemetryCallUtil {
  private static final VirtualField<Call, CallState> CALL_STATE =
      VirtualField.find(Call.class, CallState.class);

  public static void setRequestAndContext(
      Object call, @Nullable RequestAndContext requestAndContext) {
    CALL_STATE.set(
        (Call) call, requestAndContext == null ? null : new CallState(requestAndContext));
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
    CallState callState = CALL_STATE.get(call);
    if (callState != null) {
      callState.setNetworkPeer(inetAddress.getHostAddress(), inetSocketAddress.getPort());
    }
  }

  @Nullable
  public static RequestAndContext getAndClearRequestAndContext(Object call) {
    Call hbaseCall = (Call) call;
    CallState callState = CALL_STATE.get(hbaseCall);
    if (callState == null) {
      return null;
    }

    RequestAndContext requestAndContext = callState.claim();
    CALL_STATE.set(hbaseCall, null);
    return requestAndContext;
  }

  @Nullable
  public static RequestAndContext getAndClearRequestAndContextIfError(
      Object call, @Nullable IOException callError, IOException expectedError) {
    Call hbaseCall = (Call) call;
    if (expectedError == null || callError != expectedError) {
      return null;
    }

    CallState callState = CALL_STATE.get(hbaseCall);
    if (callState == null) {
      return null;
    }

    RequestAndContext requestAndContext = callState.claim();
    CALL_STATE.set(hbaseCall, null);
    return requestAndContext;
  }

  static final class CallState {
    private final RequestAndContext requestAndContext;
    private final Object lock = new Object();
    @Nullable private String networkPeerAddress;
    private int networkPeerPort;
    private boolean completed;

    CallState(RequestAndContext requestAndContext) {
      this.requestAndContext = requestAndContext;
    }

    void setNetworkPeer(String networkPeerAddress, int networkPeerPort) {
      synchronized (lock) {
        if (!completed) {
          this.networkPeerAddress = networkPeerAddress;
          this.networkPeerPort = networkPeerPort;
        }
      }
    }

    @Nullable
    RequestAndContext claim() {
      String networkPeerAddress;
      int networkPeerPort;
      synchronized (lock) {
        if (completed) {
          return null;
        }
        completed = true;
        networkPeerAddress = this.networkPeerAddress;
        networkPeerPort = this.networkPeerPort;
      }

      if (networkPeerAddress != null) {
        requestAndContext.getRequest().setNetworkPeer(networkPeerAddress, networkPeerPort);
      }
      return requestAndContext;
    }
  }

  private OpenTelemetryCallUtil() {}
}
