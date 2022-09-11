/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.util.SpanNames;
import org.springframework.web.method.HandlerMethod;

public class WebfluxSpanNameExtractor implements SpanNameExtractor<Object> {

  private final SpanNameExtractor<Object> handlerCodeAttributesGetter =
      CodeSpanNameExtractor.create(new HandlerCodeAttributesGetter());

  @Override
  public String extract(Object handler) {
    if (handler instanceof HandlerMethod) {
      // Special case for requests mapped with annotations
      HandlerMethod handlerMethod = (HandlerMethod) handler;
      return SpanNames.fromMethod(handlerMethod.getMethod());
    } else {
      return handlerCodeAttributesGetter.extract(handler);
    }
  }
}
