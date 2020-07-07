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

package io.opentelemetry.instrumentation.springwebmvc;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerTracerBase;
import io.opentelemetry.auto.instrumentation.servlet.HttpServletRequestGetter;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.Controller;

public class SpringWebMvcDecorator
    extends HttpServerTracerBase<HttpServletRequest, HttpServletRequest, HttpServletRequest> {

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.spring-webmvc-3.1");

  public static final SpringWebMvcDecorator DECORATE = new SpringWebMvcDecorator();

  public void updateSpanNameUsingPattern(final Span span, final HttpServletRequest request) {
    if (request != null) {
      final Object bestMatchingPattern =
          request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      if (bestMatchingPattern != null) {
        span.updateName(bestMatchingPattern.toString());
      }
    }
  }

  public String spanNameOnHandle(final Object handler) {
    final Class<?> clazz;
    final String methodName;

    if (handler instanceof HandlerMethod) {
      // name span based on the class and method name defined in the handler
      final Method method = ((HandlerMethod) handler).getMethod();
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

    return DECORATE.spanNameForMethod(clazz, methodName);
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   *
   * @param methodName the name of the method to get the name from, nullable
   * @return the span name from the class and method
   */
  public String spanNameForMethod(final Class<?> clazz, final String methodName) {
    if (methodName == null) {
      return spanNameForClass(clazz);
    }
    return spanNameForClass(clazz) + "." + methodName;
  }

  public String spanNameOnRender(final ModelAndView mv) {
    final String viewName = mv.getViewName();
    if (viewName != null) {
      return "Render " + viewName;
    }
    final View view = mv.getView();
    if (view != null) {
      return "Render " + view.getClass().getSimpleName();
    }
    // either viewName or view should be non-null, but just in case
    return "Render <unknown>";
  }

  public Span onRender(final Span span, final ModelAndView mv) {
    span.setAttribute("view.name", mv.getViewName());
    final View view = mv.getView();
    if (view != null) {
      span.setAttribute("view.type", spanNameForClass(view.getClass()));
    }
    return span;
  }

  public Span afterStart(final Span span) {
    assert span != null;
    return span;
  }

  public Span beforeFinish(final Span span) {
    assert span != null;
    return span;
  }

  @Override
  public void onError(final Span span, final Throwable throwable) {
    assert span != null;
    if (throwable != null) {
      span.setStatus(Status.UNKNOWN);
      addThrowable(span, throwable);
    }
  }

  @Override
  protected Integer peerPort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  @Override
  protected String peerHostIP(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  @Override
  protected Getter<HttpServletRequest> getGetter() {
    return HttpServletRequestGetter.GETTER;
  }

  @Override
  protected URI url(HttpServletRequest request) throws URISyntaxException {
    return new URI(request.getRequestURI());
  }

  @Override
  protected String method(HttpServletRequest request) {
    return request.getMethod();
  }

  @Override
  protected void attachServerContext(Context context, HttpServletRequest request) {
    request.setAttribute(CONTEXT_ATTRIBUTE, context);
  }

  @Override
  public Context getServerContext(HttpServletRequest request) {
    Object context = request.getAttribute(CONTEXT_ATTRIBUTE);
    return context instanceof Context ? (Context) context : null;
  }
}
