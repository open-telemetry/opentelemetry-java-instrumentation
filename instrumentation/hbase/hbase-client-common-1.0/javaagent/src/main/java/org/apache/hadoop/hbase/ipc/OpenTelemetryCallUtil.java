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
  // HBase's native Call completion guard ensures only one terminal advice consumes this
  // association.
  private static final VirtualField<Call, RequestAndContext> REQUEST_AND_CONTEXT =
      VirtualField.find(Call.class, RequestAndContext.class);

  public static void setRequestAndContext(
      Object call, @Nullable RequestAndContext requestAndContext) {
    REQUEST_AND_CONTEXT.set((Call) call, requestAndContext);
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

    RequestAndContext requestAndContext = REQUEST_AND_CONTEXT.get((Call) message);
    if (requestAndContext != null) {
      requestAndContext.getRequest().setNetworkPeer(inetSocketAddress);
    }
  }

  @Nullable
  public static RequestAndContext getAndClearRequestAndContext(Object call) {
    Call hbaseCall = (Call) call;
    RequestAndContext requestAndContext = REQUEST_AND_CONTEXT.get(hbaseCall);
    if (requestAndContext == null) {
      return null;
    }

    REQUEST_AND_CONTEXT.set(hbaseCall, null);
    return requestAndContext;
  }

  @Nullable
  public static RequestAndContext getAndClearRequestAndContextIfError(
      Object call, @Nullable IOException callError, IOException expectedError) {
    Call hbaseCall = (Call) call;
    if (expectedError == null || callError != expectedError) {
      return null;
    }

    RequestAndContext requestAndContext = REQUEST_AND_CONTEXT.get(hbaseCall);
    if (requestAndContext == null) {
      return null;
    }

    REQUEST_AND_CONTEXT.set(hbaseCall, null);
    return requestAndContext;
  }

  private OpenTelemetryCallUtil() {}
}
