/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;

public class CallMethodHelper {
  private CallMethodHelper() {}

  public static HbaseRequest buildRequest(
      String operation, String tableName, String user, InetSocketAddress addr) {
    return HbaseRequest.create(operation, tableName, user, addr.getHostString(), addr.getPort());
  }

  public static void handleOnExit(
      Throwable throwable,
      Scope scope,
      Context context,
      HbaseRequest request,
      Instrumenter<HbaseRequest, Void> instrumenter,
      boolean end) {
    if (scope == null) {
      return;
    }
    scope.close();
    if (end || throwable != null) {
      instrumenter.end(context, request, null, throwable);
    }
  }

  public static void handleOnEnter(
      Throwable throwable, Object call, Instrumenter<HbaseRequest, Void> instrumenter) {
    VirtualField<Object, RequestAndContext> virtualField =
        VirtualField.find(Object.class, RequestAndContext.class);
    RequestAndContext requestAndContext = virtualField.get(call);
    if (requestAndContext == null) {
      return;
    }
    Context context = requestAndContext.getContext();
    HbaseRequest request = requestAndContext.getRequest();
    if (context == null || request == null) {
      return;
    }
    instrumenter.end(context, request, null, throwable);
  }
}
