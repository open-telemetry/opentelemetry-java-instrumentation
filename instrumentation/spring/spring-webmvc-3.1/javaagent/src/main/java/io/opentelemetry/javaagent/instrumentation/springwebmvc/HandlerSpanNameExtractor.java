/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springwebmvc;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNames;
import java.lang.reflect.Method;
import javax.servlet.Servlet;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.Controller;

public class HandlerSpanNameExtractor implements SpanNameExtractor<Object> {
  @Override
  public String extract(Object handler) {
    Class<?> clazz;
    String methodName;

    if (handler instanceof HandlerMethod) {
      // name span based on the class and method name defined in the handler
      Method method = ((HandlerMethod) handler).getMethod();
      clazz = method.getDeclaringClass();
      methodName = method.getName();
    } else if (handler instanceof HttpRequestHandler) {
      // org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter
      clazz = handler.getClass();
      methodName = "handleRequest";
    } else if (handler instanceof Controller) {
      // org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter
      clazz = handler.getClass();
      methodName = "handleRequest";
    } else if (handler instanceof Servlet) {
      // org.springframework.web.servlet.handler.SimpleServletHandlerAdapter
      clazz = handler.getClass();
      methodName = "service";
    } else {
      // perhaps org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter
      clazz = handler.getClass();
      methodName = "<annotation>";
    }

    return SpanNames.fromMethod(clazz, methodName);
  }
}
