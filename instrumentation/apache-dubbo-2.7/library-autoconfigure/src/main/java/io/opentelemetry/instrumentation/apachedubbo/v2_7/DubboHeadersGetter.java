/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import org.apache.dubbo.rpc.RpcInvocation;

enum DubboHeadersGetter implements TextMapGetter<DubboRequest> {
  INSTANCE;

  private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

  @Override
  @SuppressWarnings("unchecked") // unchecked for 2.7.6, 2.7.7
  public Iterable<String> keys(DubboRequest request) {
    RpcInvocation invocation = request.invocation();
    try {
      // In 2.7.6, 2.7.7, the StringToObjectMap implementation does not correctly retrieve the
      // keySet. Therefore, it's advisable to always call getObjectAttachments when it is available.
      MethodHandle getObjectAttachments =
          lookup.findStatic(
              RpcInvocation.class, "getObjectAttachments", MethodType.methodType(void.class));
      return ((Map<String, Object>) getObjectAttachments.invoke(invocation)).keySet();
    } catch (Throwable t) {
      // ignore
    }
    return invocation.getAttachments().keySet();
  }

  @Override
  @SuppressWarnings("deprecation") // deprecation for dubbo 3.2.15
  public String get(DubboRequest request, String key) {
    return request.invocation().getAttachment(key);
  }
}
