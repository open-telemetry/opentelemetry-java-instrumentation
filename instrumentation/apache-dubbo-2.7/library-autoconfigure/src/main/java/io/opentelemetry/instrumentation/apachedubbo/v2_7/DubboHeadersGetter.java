/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import org.apache.dubbo.rpc.RpcInvocation;

enum DubboHeadersGetter implements TextMapGetter<DubboRequest> {
  INSTANCE;

  @Override
  @SuppressWarnings("unchecked") // unchecked for 2.7.6, 2.7.7
  public Iterable<String> keys(DubboRequest request) {
    RpcInvocation invocation = request.invocation();
    Map<String, String> attachments = invocation.getAttachments();
    Set<String> keys = invocation.getAttachments().keySet();
    // In 2.7.6, 2.7.7, the StringToObjectMap implementation does not correctly retrieve the keySet.
    if (keys.size() == 0 && "ObjectToStringMap".equals(attachments.getClass().getSimpleName())) {
      Method getObjectAttachmentsMethod = null;
      try {
        getObjectAttachmentsMethod = invocation.getClass().getMethod("getObjectAttachments");
        return ((Map<String, Object>) getObjectAttachmentsMethod.invoke(invocation)).keySet();
      } catch (Exception e) {
        // ignore
      }
    }
    return keys;
  }

  @Override
  @SuppressWarnings("deprecation") // deprecation for dubbo 3.2.15
  public String get(DubboRequest request, String key) {
    return request.invocation().getAttachment(key);
  }
}
