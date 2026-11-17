/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v6_0;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.util.ServletRequestPathUtils.PATH_ATTRIBUTE;

import io.opentelemetry.context.Context;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

class HttpRouteSupportTest {
  @Test
  void refreshReplacesSnapshotAndPreservesFallbackAndRequestPath() throws Exception {
    MockServletContext servletContext = new MockServletContext();
    try (GenericWebApplicationContext context = new GenericWebApplicationContext(servletContext)) {
      DispatcherServlet servlet = new DispatcherServlet();
      context.getBeanFactory().registerSingleton("dispatcherServlet", servlet);
      context.refresh();
      servletContext.setAttribute(
          WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);

      HttpRouteSupport support = new HttpRouteSupport();
      support.onFilterInit(new MockFilterConfig(servletContext));
      List<HandlerMapping> mappings = new ArrayList<>();
      mappings.add(mapping(context, "/first/{id}"));
      ReflectionTestUtils.setField(servlet, "handlerMappings", mappings);
      context.publishEvent(new ContextRefreshedEvent(context));

      assertThat(support.hasMappings()).isTrue();
      mappings.clear();
      MockHttpServletRequest first = new MockHttpServletRequest("GET", "/first/1");
      Object previousPath = new Object();
      first.setAttribute(PATH_ATTRIBUTE, previousPath);
      assertThat(support.getHttpRoute(Context.root(), first)).isEqualTo("/first/{id}");
      assertThat(first.getAttribute(PATH_ATTRIBUTE)).isSameAs(previousPath);

      ReflectionTestUtils.setField(
          servlet, "handlerMappings", singletonList(mapping(context, "/second/{id}")));
      context.publishEvent(new ContextRefreshedEvent(context));
      assertThat(support.hasMappings()).isTrue();
      MockHttpServletRequest second = new MockHttpServletRequest("GET", "/second/2");
      assertThat(support.getHttpRoute(Context.root(), second)).isEqualTo("/second/{id}");
      assertThat(second.getAttribute(PATH_ATTRIBUTE)).isNull();
      assertThat(support.getHttpRoute(Context.root(), first)).isNull();
      assertThat(first.getAttribute(PATH_ATTRIBUTE)).isSameAs(previousPath);

      ReflectionTestUtils.setField(servlet, "handlerMappings", emptyList());
      context.publishEvent(new ContextRefreshedEvent(context));
      assertThat(support.hasMappings()).isTrue();
      assertThat(support.getHttpRoute(Context.root(), second)).isEqualTo("/second/{id}");
    }
  }

  private static HandlerMapping mapping(GenericWebApplicationContext context, String path)
      throws Exception {
    PathPatternParser parser = new PathPatternParser();
    RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
    mapping.setPatternParser(parser);
    mapping.setApplicationContext(context);
    mapping.afterPropertiesSet();
    RequestMappingInfo.BuilderConfiguration options = new RequestMappingInfo.BuilderConfiguration();
    options.setPatternParser(parser);
    mapping.registerMapping(
        RequestMappingInfo.paths(path).options(options).build(),
        new Controller(),
        Controller.class.getMethod("handle"));
    return mapping;
  }

  public static class Controller {
    public void handle() {}
  }
}
