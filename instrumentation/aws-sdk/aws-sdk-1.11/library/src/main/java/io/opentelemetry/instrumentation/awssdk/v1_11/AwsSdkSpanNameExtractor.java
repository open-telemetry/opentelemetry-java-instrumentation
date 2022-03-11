/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.concurrent.ConcurrentHashMap;

class AwsSdkSpanNameExtractor implements SpanNameExtractor<Request<?>> {

  private final AwsSdkRpcAttributesGetter rpcAttributes = AwsSdkRpcAttributesGetter.INSTANCE;
  private final NamesCache namesCache = new NamesCache();

  @Override
  public String extract(Request<?> request) {
    return qualifiedOperation(
        rpcAttributes.service(request),
        rpcAttributes.method(request),
        request.getOriginalRequest().getClass());
  }

  private String qualifiedOperation(String service, String operation, Class<?> requestClass) {
    ConcurrentHashMap<String, String> cache = namesCache.get(requestClass);
    return cache.computeIfAbsent(service, s -> s.replace("Amazon", "").trim() + '.' + operation);
  }

  static final class NamesCache extends ClassValue<ConcurrentHashMap<String, String>> {
    @Override
    protected ConcurrentHashMap<String, String> computeValue(Class<?> type) {
      return new ConcurrentHashMap<>();
    }
  }
}
