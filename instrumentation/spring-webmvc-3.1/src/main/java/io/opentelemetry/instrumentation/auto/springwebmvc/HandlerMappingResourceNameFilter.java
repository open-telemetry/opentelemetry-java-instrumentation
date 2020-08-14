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

import static io.opentelemetry.instrumentation.auto.springwebmvc.SpringWebMvcTracer.TRACER;

import io.opentelemetry.trace.Span;
import java.io.IOException;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

public class HandlerMappingResourceNameFilter extends OncePerRequestFilter implements Ordered {
  private volatile List<HandlerMapping> handlerMappings;

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    Span serverSpan = TRACER.getCurrentServerSpan();

    if (handlerMappings != null && serverSpan != null) {
      try {
        if (findMapping(request)) {
          // Name the parent span based on the matching pattern
          // Let the parent span resource name be set with the attribute set in findMapping.
          TRACER.onRequest(serverSpan, request);
        }
      } catch (final Exception ignored) {
        // mapping.getHandler() threw exception.  Ignore
      }
    }

    filterChain.doFilter(request, response);
  }

  /**
   * When a HandlerMapping matches a request, it sets HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
   * as an attribute on the request. This attribute is read by SpringWebMvcDecorator.onRequest and
   * set as the resource name.
   */
  private boolean findMapping(final HttpServletRequest request) throws Exception {
    for (HandlerMapping mapping : handlerMappings) {
      HandlerExecutionChain handler = mapping.getHandler(request);
      if (handler != null) {
        return true;
      }
    }
    return false;
  }

  public void setHandlerMappings(final List<HandlerMapping> handlerMappings) {
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
