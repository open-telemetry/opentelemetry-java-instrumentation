/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.springwebmvc;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.Controller;

public class SpringWebMvcTracer extends BaseTracer {

  public static final SpringWebMvcTracer TRACER = new SpringWebMvcTracer();

  public Span startHandlerSpan(Object handler) {
    return tracer.spanBuilder(spanNameOnHandle(handler)).startSpan();
  }

  public Span startSpan(ModelAndView mv) {
    Span span = tracer.spanBuilder(spanNameOnRender(mv)).startSpan();
    onRender(span, mv);
    return span;
  }

  public void onRequest(Span span, HttpServletRequest request) {
    if (request != null) {
      Object bestMatchingPattern =
          request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      if (bestMatchingPattern != null) {
        span.updateName(bestMatchingPattern.toString());
      }
    }
  }

  private String spanNameOnHandle(Object handler) {
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

    return spanNameForMethod(clazz, methodName);
  }

  private String spanNameOnRender(ModelAndView mv) {
    String viewName = mv.getViewName();
    if (viewName != null) {
      return "Render " + viewName;
    }
    View view = mv.getView();
    if (view != null) {
      return "Render " + view.getClass().getSimpleName();
    }
    // either viewName or view should be non-null, but just in case
    return "Render <unknown>";
  }

  private void onRender(Span span, ModelAndView mv) {
    span.setAttribute("view.name", mv.getViewName());
    View view = mv.getView();
    if (view != null) {
      span.setAttribute("view.type", spanNameForClass(view.getClass()));
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.spring-webmvc-3.1";
  }
}
