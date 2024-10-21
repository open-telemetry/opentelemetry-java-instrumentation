/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.util.SpanNames;
import org.springframework.web.method.HandlerMethod;

public class WebfluxSpanNameExtractor implements SpanNameExtractor<Object> {

  private final SpanNameExtractor<Object> handlerSpanNameExtractor =
      CodeSpanNameExtractor.create(new HandlerCodeAttributesGetter());

  @Override
  public String extract(Object handler) {
    if (handler instanceof HandlerMethod) {
      // Special case for requests mapped with annotations
      HandlerMethod handlerMethod = (HandlerMethod) handler;
      return SpanNames.fromMethod(handlerMethod.getMethod());
    } else {
      return handlerSpanNameExtractor.extract(handler);
    }
  }
}
