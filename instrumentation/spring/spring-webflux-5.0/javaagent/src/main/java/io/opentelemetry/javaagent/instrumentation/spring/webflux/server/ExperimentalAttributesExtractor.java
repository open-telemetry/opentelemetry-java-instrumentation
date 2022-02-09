/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.springframework.web.method.HandlerMethod;

public class ExperimentalAttributesExtractor implements AttributesExtractor<Object, Void> {

  private static final AttributeKey<String> HANDLER_TYPE =
      AttributeKey.stringKey("spring-webflux.handler.type");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Object handler) {
    attributes.put(HANDLER_TYPE, getHandlerType(handler));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      Object handler,
      @Nullable Void unused,
      @Nullable Throwable error) {}

  private static String getHandlerType(Object handler) {
    if (handler instanceof HandlerMethod) {
      // Special case for requests mapped with annotations
      HandlerMethod handlerMethod = (HandlerMethod) handler;
      return handlerMethod.getMethod().getDeclaringClass().getName();
    } else {
      return handler.getClass().getName();
    }
  }
}
