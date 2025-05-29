/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import javax.annotation.Nullable;
import org.springframework.web.method.HandlerMethod;

public class HandlerCodeAttributesGetter implements CodeAttributesGetter<Object> {
  @Nullable
  @Override
  public Class<?> getCodeClass(Object handler) {
    if (handler instanceof HandlerMethod) {
      // Special case for requests mapped with annotations
      HandlerMethod handlerMethod = (HandlerMethod) handler;
      return handlerMethod.getMethod().getDeclaringClass();
    } else {
      return handler.getClass();
    }
  }

  @Nullable
  @Override
  public String getMethodName(Object handler) {
    if (handler instanceof HandlerMethod) {
      HandlerMethod handlerMethod = (HandlerMethod) handler;
      return handlerMethod.getMethod().getName();
    }
    return "handle";
  }
}
