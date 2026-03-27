/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.Controller;

public class HandlerCodeAttributesGetter implements CodeAttributesGetter<Object> {

  private static final ClassValue<Boolean> servletHandler =
      new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
          return hasTypeNamed(type, "javax.servlet.Servlet")
              || hasTypeNamed(type, "jakarta.servlet.Servlet");
        }
      };

  @Nullable
  @Override
  public Class<?> getCodeClass(Object handler) {
    if (handler instanceof HandlerMethod) {
      // name span based on the class and method name defined in the handler
      Method method = ((HandlerMethod) handler).getMethod();
      return method.getDeclaringClass();
    } else {
      return handler.getClass();
    }
  }

  @Nullable
  @Override
  public String getMethodName(Object handler) {
    if (handler instanceof HandlerMethod) {
      // name span based on the class and method name defined in the handler
      Method method = ((HandlerMethod) handler).getMethod();
      return method.getName();
    } else if (handler instanceof HttpRequestHandler) {
      // org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter
      return "handleRequest";
    } else if (handler instanceof Controller) {
      // org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter
      return "handleRequest";
    } else if (isServlet(handler)) {
      // org.springframework.web.servlet.handler.SimpleServletHandlerAdapter
      return "service";
    } else {
      // perhaps org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter
      return "<annotation>";
    }
  }

  private static boolean isServlet(Object handler) {
    return servletHandler.get(handler.getClass());
  }

  private static boolean hasTypeNamed(@Nullable Class<?> type, String className) {
    while (type != null) {
      if (type.getName().equals(className)) {
        return true;
      }
      for (Class<?> interfaceType : type.getInterfaces()) {
        if (hasTypeNamed(interfaceType, className)) {
          return true;
        }
      }
      type = type.getSuperclass();
    }
    return false;
  }
}
