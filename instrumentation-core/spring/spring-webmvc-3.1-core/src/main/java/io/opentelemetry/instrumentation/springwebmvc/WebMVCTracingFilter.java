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

import static io.opentelemetry.instrumentation.springwebmvc.SpringWebMvcDecorator.DECORATE;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.io.IOException;
import java.lang.reflect.Method;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

public class WebMVCTracingFilter extends OncePerRequestFilter implements Ordered {

  private static final String CLASS_NAME = "WebMVCTracingFilter";
  private final Tracer tracer;

  public WebMVCTracingFilter(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    HttpServletRequest req = (HttpServletRequest) request;

    Method doFilterInteral = this.getClass().getEnclosingMethod();
    Span serverSpan = DECORATE.startSpan(tracer, request, doFilterInteral, CLASS_NAME);

    try (Scope scope = tracer.withSpan(serverSpan)) {
      filterChain.doFilter(req, response);
    } catch (Throwable t) {
      DECORATE.onError(serverSpan, t);
    } finally {
      DECORATE.end(serverSpan, response.getStatus());
    }
  }

  @Override
  public void destroy() {}

  @Override
  public int getOrder() {
    // Run after all HIGHEST_PRECEDENCE items
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }
}
