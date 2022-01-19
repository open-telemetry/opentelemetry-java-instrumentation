/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNames;
import org.springframework.web.method.HandlerMethod;

public class WebfluxSpanNameExtractor implements SpanNameExtractor<Object> {
  @Override
  public String extract(Object handler) {
    if (handler instanceof HandlerMethod) {
      // Special case for requests mapped with annotations
      HandlerMethod handlerMethod = (HandlerMethod) handler;
      return SpanNames.fromMethod(handlerMethod.getMethod());
    } else {
      return AdviceUtils.spanNameForHandler(handler);
    }
  }
}
