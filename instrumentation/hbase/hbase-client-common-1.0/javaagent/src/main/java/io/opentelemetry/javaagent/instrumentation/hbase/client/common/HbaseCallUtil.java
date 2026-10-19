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

public final class HbaseCallUtil {

  private static final String CALL_CLASS_NAME = "org.apache.hadoop.hbase.ipc.Call";

  @Nullable private static final Class<?> CALL_CLASS = loadCallClass();

  @Nullable
  private static final VirtualField<Object, RequestAndContext> REQUEST_AND_CONTEXT =
      findRequestAndContextVirtualField(CALL_CLASS);

  private static Class<?> loadCallClass() {
    try {
      return Class.forName(CALL_CLASS_NAME, false, HbaseCallUtil.class.getClassLoader());
    } catch (ClassNotFoundException | SecurityException | LinkageError ignored) {
      return null;
    }
  }

  @Nullable
  @NoMuzzle
  @SuppressWarnings("unchecked") // The runtime carrier class is not known at compile time.
  private static VirtualField<Object, RequestAndContext> findRequestAndContextVirtualField(
      @Nullable Class<?> callClass) {
    if (callClass == null) {
      return null;
    }
    return (VirtualField<Object, RequestAndContext>)
        (VirtualField<?, RequestAndContext>) VirtualField.find(callClass, RequestAndContext.class);
  }

  public static void setRequestAndContext(
      Object call, @Nullable RequestAndContext requestAndContext) {
    if (isCall(call) && REQUEST_AND_CONTEXT != null) {
      REQUEST_AND_CONTEXT.set(call, requestAndContext);
    }
  }

  public static boolean isCall(Object message) {
    return CALL_CLASS != null && CALL_CLASS.isInstance(message);
  }

  public static void setNetworkPeer(Object message, @Nullable SocketAddress remoteAddress) {
    if (!isCall(message)
        || !(remoteAddress instanceof InetSocketAddress)
        || REQUEST_AND_CONTEXT == null) {
      return;
    }

    InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;
    InetAddress inetAddress = inetSocketAddress.getAddress();
    if (inetAddress == null) {
      return;
    }

    RequestAndContext requestAndContext = REQUEST_AND_CONTEXT.get(message);
    if (requestAndContext == null) {
      return;
    }

    requestAndContext.getRequest().setNetworkPeer(inetSocketAddress);
  }

  @Nullable
  public static RequestAndContext getAndClearRequestAndContext(Object call) {
    if (!isCall(call) || REQUEST_AND_CONTEXT == null) {
      return null;
    }

    RequestAndContext requestAndContext = REQUEST_AND_CONTEXT.get(call);
    if (requestAndContext == null) {
      return null;
    }

    REQUEST_AND_CONTEXT.set(call, null);
    return requestAndContext;
  }

  @Nullable
  public static RequestAndContext getAndClearRequestAndContextIfError(
      Object call, @Nullable IOException callError, IOException expectedError) {
    if (!isCall(call)
        || expectedError == null
        || callError != expectedError
        || REQUEST_AND_CONTEXT == null) {
      return null;
    }

    RequestAndContext requestAndContext = REQUEST_AND_CONTEXT.get(call);
    if (requestAndContext == null) {
      return null;
    }

    REQUEST_AND_CONTEXT.set(call, null);
    return requestAndContext;
  }

  private HbaseCallUtil() {}
}
