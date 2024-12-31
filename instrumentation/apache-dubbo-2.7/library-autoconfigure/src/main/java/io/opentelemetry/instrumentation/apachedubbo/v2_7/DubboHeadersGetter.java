/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.lang.reflect.Method;
import java.util.Map;
import org.apache.dubbo.rpc.RpcInvocation;

enum DubboHeadersGetter implements TextMapGetter<DubboRequest> {
  INSTANCE;

  @Override
  @SuppressWarnings("unchecked") // unchecked for dubbo 2.7.6
  public Iterable<String> keys(DubboRequest request) {
    RpcInvocation invocation = request.invocation();
    Map<String, String> attachments = invocation.getAttachments();
    // in 2.7.6+, type of attachments is StringToObjectMap, it doesn't contain keySet method.
    if ("ObjectToStringMap".equals(attachments.getClass().getSimpleName())) {
      Method getObjectAttachmentsMethod = null;
      try {
        getObjectAttachmentsMethod = invocation.getClass().getMethod("getObjectAttachments");
        return ((Map<String, Object>) getObjectAttachmentsMethod.invoke(invocation)).keySet();
      } catch (Exception e) {
        // ignore
      }
    }
    return invocation.getAttachments().keySet();
  }

  @Override
  @SuppressWarnings("deprecation") // deprecation for dubbo 3.2.15
  public String get(DubboRequest request, String key) {
    return request.invocation().getAttachment(key);
  }
}
