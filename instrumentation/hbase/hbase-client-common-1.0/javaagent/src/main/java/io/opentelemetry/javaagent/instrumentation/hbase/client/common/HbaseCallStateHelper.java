/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

// Shared, carrier-agnostic logic for the RequestAndContext state attached to each hbase Call.
// The concrete VirtualField<Call, RequestAndContext> handle is created inline in the
// package-private-adjacent @Advice methods that need access to the package-private Call class,
// then passed into these generic helpers.
public final class HbaseCallStateHelper {

  @Nullable
  public static <T> RequestAndContext getAndClear(
      VirtualField<T, RequestAndContext> field, T carrier) {
    RequestAndContext requestAndContext = field.get(carrier);
    if (requestAndContext == null) {
      return null;
    }
    field.set(carrier, null);
    return requestAndContext;
  }

  @Nullable
  public static <T> RequestAndContext getAndClearIfError(
      VirtualField<T, RequestAndContext> field,
      T carrier,
      @Nullable IOException callError,
      IOException expectedError) {
    if (expectedError == null || callError != expectedError) {
      return null;
    }
    return getAndClear(field, carrier);
  }

  public static <T> void updateNetworkPeer(
      VirtualField<T, RequestAndContext> field, T carrier, @Nullable SocketAddress remoteAddress) {
    if (!(remoteAddress instanceof InetSocketAddress)) {
      return;
    }
    InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;
    InetAddress inetAddress = inetSocketAddress.getAddress();
    if (inetAddress == null) {
      return;
    }
    RequestAndContext requestAndContext = field.get(carrier);
    if (requestAndContext != null) {
      requestAndContext.getRequest().setNetworkPeer(inetSocketAddress);
    }
  }

  private HbaseCallStateHelper() {}
}
