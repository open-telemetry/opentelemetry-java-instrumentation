/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3;

import static java.util.Objects.requireNonNull;
import static org.springframework.web.util.ServletRequestPathUtils.PATH_ATTRIBUTE;

import io.opentelemetry.context.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

final class HttpRouteSupport {

  private final AtomicBoolean contextRefreshTriggered = new AtomicBoolean();
  @Nullable private volatile DispatcherServlet dispatcherServlet;
  @Nullable private volatile List<HandlerMapping> handlerMappings;
  private volatile boolean parseRequestPath;

  void onFilterInit(FilterConfig filterConfig) {
    WebApplicationContext context =
        WebApplicationContextUtils.getWebApplicationContext(filterConfig.getServletContext());
    if (!(context instanceof ConfigurableWebApplicationContext)) {
      return;
    }

    DispatcherServlet servlet = context.getBeanProvider(DispatcherServlet.class).getIfAvailable();
    if (servlet != null) {
      dispatcherServlet = servlet;

      ((ConfigurableWebApplicationContext) context)
          .addApplicationListener(new WebContextRefreshListener());
    }
  }

  // we can't retrieve the handler mappings from the DispatcherServlet in the onRefresh listener,
  // because it loads them just after the application context refreshed event is processed
  // to work around this, we're setting a boolean flag that'll cause this filter to load the
  // mappings the next time it attempts to set the http.route
  final class WebContextRefreshListener implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
      contextRefreshTriggered.set(true);
    }
  }

  boolean hasMappings() {
    if (contextRefreshTriggered.compareAndSet(true, false)) {
      // reload the handler mappings only if the web app context was recently refreshed
      DispatcherServlet dispatcherServlet = this.dispatcherServlet;
      if (dispatcherServlet != null) {
        List<HandlerMapping> mappings = dispatcherServlet.getHandlerMappings();
        if (mappings != null) {
          setHandlerMappings(mappings);
        }
      }
    }
    return handlerMappings != null;
  }

  private void setHandlerMappings(List<HandlerMapping> mappings) {
    List<HandlerMapping> handlerMappings = new ArrayList<>();
    for (HandlerMapping mapping : mappings) {
      // Originally we ran findMapping at the very beginning of the request. This turned out to have
      // application-crashing side-effects with grails. That is why we don't add all HandlerMapping
      // classes here. Although now that we run findMapping after the request, and only when server
      // span name has not been updated by a controller, the probability of bad side-effects is much
      // reduced even if we did add all HandlerMapping classes here.
      if (mapping instanceof RequestMappingHandlerMapping) {
        handlerMappings.add(mapping);
        if (mapping.usesPathPatterns()) {
          this.parseRequestPath = true;
        }
      }
    }
    if (!handlerMappings.isEmpty()) {
      this.handlerMappings = handlerMappings;
    }
  }

  @Nullable
  String getHttpRoute(Context context, @Nullable HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    boolean parsePath = this.parseRequestPath;
    Object previousValue = null;
    if (parsePath) {
      previousValue = request.getAttribute(PATH_ATTRIBUTE);
      // sets new value for PATH_ATTRIBUTE of request
      ServletRequestPathUtils.parseAndCache(request);
    }
    try {
      if (findMapping(request)) {
        // Name the parent span based on the matching pattern
        // Let the parent span resource name be set with the attribute set in findMapping.
        Object bestMatchingPattern =
            request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (bestMatchingPattern != null) {
          return prependContextPath(request, bestMatchingPattern.toString());
        }
      }
    } finally {
      // mimic spring DispatcherServlet and restore the previous value of PATH_ATTRIBUTE
      if (parsePath) {
        if (previousValue == null) {
          request.removeAttribute(PATH_ATTRIBUTE);
        } else {
          request.setAttribute(PATH_ATTRIBUTE, previousValue);
        }
      }
    }
    return null;
  }

  /**
   * When a HandlerMapping matches a request, it sets HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
   * as an attribute on the request. This attribute set as the HTTP route.
   */
  private boolean findMapping(HttpServletRequest request) {
    try {
      // handlerMapping already null-checked above
      for (HandlerMapping mapping : requireNonNull(handlerMappings)) {
        HandlerExecutionChain handler = mapping.getHandler(request);
        if (handler != null) {
          return true;
        }
      }
    } catch (Exception ignored) {
      // mapping.getHandler() threw exception. Ignore
    }
    return false;
  }

  private static String prependContextPath(HttpServletRequest request, String route) {
    String contextPath = request.getContextPath();
    if (contextPath == null) {
      return route;
    }
    return contextPath + (route.startsWith("/") ? route : ("/" + route));
  }
}
