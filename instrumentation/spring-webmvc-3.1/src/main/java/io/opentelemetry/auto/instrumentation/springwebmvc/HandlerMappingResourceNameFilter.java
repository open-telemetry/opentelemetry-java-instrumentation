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

package io.opentelemetry.auto.instrumentation.springwebmvc;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.springwebmvc.SpringWebMvcDecorator.DECORATE;

import io.opentelemetry.trace.Span;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

public class HandlerMappingResourceNameFilter implements Filter {
  private volatile List<HandlerMapping> handlerMappings;

  @Override
  public void init(final FilterConfig filterConfig) {}

  @Override
  public void doFilter(
      final ServletRequest servletRequest,
      final ServletResponse servletResponse,
      final FilterChain filterChain) {
    if (servletRequest instanceof HttpServletRequest && handlerMappings != null) {
      final HttpServletRequest request = (HttpServletRequest) servletRequest;
      try {
        if (findMapping(request)) {
          // Name the parent span based on the matching pattern
          final Object parentSpan = request.getAttribute(SPAN_ATTRIBUTE);
          if (parentSpan instanceof Span) {
            // Let the parent span resource name be set with the attribute set in findMapping.
            DECORATE.onRequest((Span) parentSpan, request);
          }
        }
      } catch (final Exception e) {
      }
    }
  }

  @Override
  public void destroy() {}

  /**
   * When a HandlerMapping matches a request, it sets HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
   * as an attribute on the request. This attribute is read by
   * SpringWebHttpServerDecorator.onRequest and set as the resource name.
   */
  private boolean findMapping(final HttpServletRequest request) throws Exception {
    for (final HandlerMapping mapping : handlerMappings) {
      final HandlerExecutionChain handler = mapping.getHandler(request);
      if (handler != null) {
        return true;
      }
    }
    return false;
  }

  public void setHandlerMappings(final List<HandlerMapping> handlerMappings) {
    this.handlerMappings = handlerMappings;
  }
}
