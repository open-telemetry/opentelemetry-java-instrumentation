/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.web.method.HandlerMethod;

public class ExperimentalAttributesExtractor extends AttributesExtractor<Object, Void> {

  private static final AttributeKey<String> HANDLER_TYPE =
      AttributeKey.stringKey("spring-webflux.handler.type");

  @Override
  protected void onStart(AttributesBuilder attributes, Object handler) {
    attributes.put(HANDLER_TYPE, getHandlerType(handler));
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
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
