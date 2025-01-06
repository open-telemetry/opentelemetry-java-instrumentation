/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import org.apache.dubbo.rpc.RpcInvocation;

enum DubboHeadersGetter implements TextMapGetter<DubboRequest> {
  INSTANCE;

  @Override
  @SuppressWarnings("unchecked") // unchecked for 2.7.6, 2.7.7
  public Iterable<String> keys(DubboRequest request) {
    RpcInvocation invocation = request.invocation();
    try {
      // In 2.7.6, 2.7.7, the StringToObjectMap implementation does not correctly retrieve the
      // keySet.
      // Therefore, it's advisable to always call getObjectAttachments when it is available.
      Method getObjectAttachmentsMethod = invocation.getClass().getMethod("getObjectAttachments");
      if (getObjectAttachmentsMethod != null) {
        return ((Map<String, Object>) getObjectAttachmentsMethod.invoke(invocation)).keySet();
      } else {
        return invocation.getAttachments().keySet();
      }
    } catch (Exception e) {
      // ignore
    }
    return Collections.emptyList();
  }

  @Override
  @SuppressWarnings("deprecation") // deprecation for dubbo 3.2.15
  public String get(DubboRequest request, String key) {
    return request.invocation().getAttachment(key);
  }
}
