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

  private static final MethodHandle GET_OBJECT_ATTACHMENTS;

  static {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle getObjectAttachments = null;
    try {
      getObjectAttachments =
          lookup.findVirtual(
              RpcInvocation.class, "getObjectAttachments", MethodType.methodType(Map.class));
    } catch (Throwable t) {
      // ignore
    }
    GET_OBJECT_ATTACHMENTS = getObjectAttachments;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<String> keys(DubboRequest request) {
    RpcInvocation invocation = request.invocation();
    try {
      // In 2.7.6, 2.7.7, the StringToObjectMap implementation does not correctly retrieve the
      // keySet. Therefore, it's advisable to always call getObjectAttachments when it is available.
      if (GET_OBJECT_ATTACHMENTS != null) {
        return ((Map<String, Object>) GET_OBJECT_ATTACHMENTS.invoke(invocation)).keySet();
      }
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
