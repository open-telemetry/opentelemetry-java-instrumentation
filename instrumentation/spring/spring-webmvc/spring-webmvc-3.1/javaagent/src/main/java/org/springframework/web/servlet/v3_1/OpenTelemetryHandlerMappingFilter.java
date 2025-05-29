/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.springframework.web.servlet.v3_1;

import static io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource.CONTROLLER;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1.SpringWebMvcServerSpanNaming;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public class OpenTelemetryHandlerMappingFilter implements Filter, Ordered {
  private static final Logger logger =
      Logger.getLogger(OpenTelemetryHandlerMappingFilter.class.getName());

  private static final MethodHandle usesPathPatternsMh = getUsesPathPatternsMh();
  private static final MethodHandle parseAndCacheMh = parseAndCacheMh();

  private final HttpServerRouteGetter<HttpServletRequest> serverSpanName =
      (context, request) -> {
        if (this.parseRequestPath) {
          // sets new value for PATH_ATTRIBUTE of request
          if (!parseAndCache(request)) {
            return null;
          }
        }
        if (findMapping(request)) {
          // Name the parent span based on the matching pattern
          // Let the parent span resource name be set with the attribute set in findMapping.
          return SpringWebMvcServerSpanNaming.SERVER_SPAN_NAME.get(context, request);
        }

        return null;
      };

  @Nullable private List<HandlerMapping> handlerMappings;
  private boolean parseRequestPath;

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      if (handlerMappings != null) {
        Context context = Context.current();
        HttpServerRoute.update(context, CONTROLLER, serverSpanName, prepareRequest(request));
      }
    }
  }

  @Override
  public void destroy() {}

  private static HttpServletRequest prepareRequest(ServletRequest request) {
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10379
    // Finding the request handler modifies request attributes. We are wrapping the request to avoid
    // this.
    return new HttpServletRequestWrapper((HttpServletRequest) request) {
      private final Map<String, Object> attributes = new HashMap<>();

      @Override
      public void setAttribute(String name, Object o) {
        attributes.put(name, o);
      }

      @Override
      public Object getAttribute(String name) {
        Object value = attributes.get(name);
        return value != null ? value : super.getAttribute(name);
      }

      @Override
      public void removeAttribute(String name) {
        attributes.remove(name);
      }
    };
  }

  /**
   * When a HandlerMapping matches a request, it sets HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
   * as an attribute on the request. This attribute is read by SpringWebMvcDecorator.onRequest and
   * set as the resource name.
   */
  private boolean findMapping(HttpServletRequest request) {
    try {
      // handlerMapping already null-checked above
      for (HandlerMapping mapping : Objects.requireNonNull(handlerMappings)) {
        HandlerExecutionChain handler = mapping.getHandler(request);
        if (handler != null) {
          return true;
        }
      }
    } catch (Exception ignored) {
      // mapping.getHandler() threw exception.  Ignore
    }
    return false;
  }

  public void setHandlerMappings(List<HandlerMapping> mappings) {
    List<HandlerMapping> handlerMappings = new ArrayList<>();
    for (HandlerMapping mapping : mappings) {
      // Originally we ran findMapping at the very beginning of the request. This turned out to have
      // application-crashing side-effects with grails. That is why we don't add all HandlerMapping
      // classes here. Although now that we run findMapping after the request, and only when server
      // span name has not been updated by a controller, the probability of bad side-effects is much
      // reduced even if we did add all HandlerMapping classes here.
      if (mapping instanceof RequestMappingHandlerMapping) {
        handlerMappings.add(mapping);
        if (usePathPatterns(mapping)) {
          this.parseRequestPath = true;
        }
      }
    }
    if (!handlerMappings.isEmpty()) {
      this.handlerMappings = handlerMappings;
    }
  }

  @Override
  public int getOrder() {
    // Run after all HIGHEST_PRECEDENCE items
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }

  private static MethodHandle getUsesPathPatternsMh() {
    // Method added in spring 5.3
    try {
      return MethodHandles.lookup()
          .findVirtual(
              HandlerMapping.class, "usesPathPatterns", MethodType.methodType(boolean.class));
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      return null;
    }
  }

  private static boolean usePathPatterns(HandlerMapping handlerMapping) {
    if (usesPathPatternsMh == null) {
      return false;
    }
    try {
      return (boolean) usesPathPatternsMh.invoke(handlerMapping);
    } catch (Throwable throwable) {
      throw new IllegalStateException(throwable);
    }
  }

  private static MethodHandle parseAndCacheMh() {
    // ServletRequestPathUtils added in spring 5.3
    try {
      Class<?> pathUtilsClass =
          Class.forName("org.springframework.web.util.ServletRequestPathUtils");
      Class<?> requestPathClass = Class.forName("org.springframework.http.server.RequestPath");
      return MethodHandles.lookup()
          .findStatic(
              pathUtilsClass,
              "parseAndCache",
              MethodType.methodType(requestPathClass, HttpServletRequest.class));
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
      return null;
    }
  }

  private static boolean parseAndCache(HttpServletRequest request) {
    if (parseAndCacheMh == null) {
      return false;
    }
    try {
      parseAndCacheMh.invoke(request);
      return true;
    } catch (Throwable throwable) {
      logger.log(Level.FINE, "Failed calling parseAndCache", throwable);
      return false;
    }
  }
}
