/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public final class HbaseCallStateHelper {

  private static final Class<?> CALL_CLASS = getCallClass();
  private static final VirtualField<Object, RequestAndContext> REQUEST_AND_CONTEXT =
      getRequestAndContextVirtualField();

  public static void set(Object call, @Nullable RequestAndContext requestAndContext) {
    REQUEST_AND_CONTEXT.set(call, requestAndContext);
  }

  @Nullable
  public static RequestAndContext getAndClear(Object call) {
    RequestAndContext requestAndContext = REQUEST_AND_CONTEXT.get(call);
    if (requestAndContext == null) {
      return null;
    }
    REQUEST_AND_CONTEXT.set(call, null);
    return requestAndContext;
  }

  @Nullable
  public static RequestAndContext getAndClearIfError(
      Object call, @Nullable IOException callError, IOException expectedError) {
    if (expectedError == null || callError != expectedError) {
      return null;
    }
    return getAndClear(call);
  }

  public static void updateNetworkPeer(Object call, @Nullable SocketAddress remoteAddress) {
    if (!CALL_CLASS.isInstance(call) || !(remoteAddress instanceof InetSocketAddress)) {
      return;
    }
    InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;
    InetAddress inetAddress = inetSocketAddress.getAddress();
    if (inetAddress == null) {
      return;
    }
    RequestAndContext requestAndContext = REQUEST_AND_CONTEXT.get(call);
    if (requestAndContext != null) {
      requestAndContext.getRequest().setNetworkPeer(inetSocketAddress);
    }
  }

  private static Class<?> getCallClass() {
    try {
      return Class.forName(
          "org.apache.hadoop.hbase.ipc.Call", false, HbaseCallStateHelper.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("HBase Call class is not available", e);
    }
  }

  @NoMuzzle
  @SuppressWarnings("unchecked") // virtual field key type is not known at compile time
  private static VirtualField<Object, RequestAndContext> getRequestAndContextVirtualField() {
    return (VirtualField<Object, RequestAndContext>)
        VirtualField.find(CALL_CLASS, RequestAndContext.class);
  }

  private HbaseCallStateHelper() {}
}
