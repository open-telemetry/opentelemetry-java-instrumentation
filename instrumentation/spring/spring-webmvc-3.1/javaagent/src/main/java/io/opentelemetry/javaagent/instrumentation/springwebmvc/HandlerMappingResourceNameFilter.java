/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springwebmvc;

import static io.opentelemetry.javaagent.instrumentation.springwebmvc.SpringWebMvcTracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import java.io.IOException;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

public class HandlerMappingResourceNameFilter implements Filter, Ordered {
  private volatile List<HandlerMapping> handlerMappings;

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      filterChain.doFilter(request, response);
      return;
    }

    Context context = Context.current();
    Span serverSpan = ServerSpan.fromContextOrNull(context);

    if (handlerMappings != null && serverSpan != null) {
      try {
        if (findMapping((HttpServletRequest) request)) {

          // Name the parent span based on the matching pattern
          // Let the parent span resource name be set with the attribute set in findMapping.
          tracer().onRequest(context, serverSpan, (HttpServletRequest) request);
        }
      } catch (Exception ignored) {
        // mapping.getHandler() threw exception.  Ignore
      }
    }

    filterChain.doFilter(request, response);
  }

  @Override
  public void destroy() {}

  /**
   * When a HandlerMapping matches a request, it sets HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
   * as an attribute on the request. This attribute is read by SpringWebMvcDecorator.onRequest and
   * set as the resource name.
   */
  private boolean findMapping(HttpServletRequest request) throws Exception {
    for (HandlerMapping mapping : handlerMappings) {
      HandlerExecutionChain handler = mapping.getHandler(request);
      if (handler != null) {
        return true;
      }
    }
    return false;
  }

  public void setHandlerMappings(List<HandlerMapping> handlerMappings) {
    this.handlerMappings = handlerMappings;
  }

  @Override
  public int getOrder() {
    // Run after all HIGHEST_PRECEDENCE items
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }

  public static class BeanDefinition extends GenericBeanDefinition {
    public BeanDefinition() {
      setScope(SCOPE_SINGLETON);
      setBeanClass(HandlerMappingResourceNameFilter.class);
      setBeanClassName(HandlerMappingResourceNameFilter.class.getName());
    }
  }
}
