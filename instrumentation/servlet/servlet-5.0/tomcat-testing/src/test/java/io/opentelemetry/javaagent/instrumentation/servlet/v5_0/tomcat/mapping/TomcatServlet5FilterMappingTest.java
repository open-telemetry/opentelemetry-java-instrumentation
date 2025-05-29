/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat.mapping;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

abstract class TomcatServlet5FilterMappingTest extends TomcatServlet5MappingTest {
  public void addFilter(Context servletContext, String path, Class<? extends Filter> filter)
      throws Exception {
    String name = UUID.randomUUID().toString();
    FilterDef filterDef = new FilterDef();
    filterDef.setFilter(filter.getConstructor().newInstance());
    filterDef.setFilterName(name);
    servletContext.addFilterDef(filterDef);
    FilterMap filterMap = new FilterMap();
    filterMap.setFilterName(name);
    filterMap.addURLPattern(path);
    servletContext.addFilterMap(filterMap);
  }

  public void addFilterWithServletName(
      Context servletContext, String servletName, Class<? extends Filter> filter) throws Exception {
    String name = UUID.randomUUID().toString();
    FilterDef filterDef = new FilterDef();
    filterDef.setFilter(filter.getConstructor().newInstance());
    filterDef.setFilterName(name);
    servletContext.addFilterDef(filterDef);
    FilterMap filterMap = new FilterMap();
    filterMap.setFilterName(name);
    filterMap.addServletName(servletName);
    servletContext.addFilterMap(filterMap);
  }

  public static class TestFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void doFilter(
        ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {
      if (servletRequest.getAttribute("firstFilterCalled") != null) {
        servletRequest.setAttribute("testFilterCalled", Boolean.TRUE);
        filterChain.doFilter(servletRequest, servletResponse);
      } else {
        throw new IllegalStateException("First filter should have been called.");
      }
    }

    @Override
    public void destroy() {}
  }

  public static class FirstFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void doFilter(
        ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {
      servletRequest.setAttribute("firstFilterCalled", Boolean.TRUE);
      filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {}
  }

  public static class LastFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void doFilter(
        ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {
      if (servletRequest.getAttribute("testFilterCalled") != null) {
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        response.getWriter().write("Ok");
        response.setStatus(HttpServletResponse.SC_OK);
      } else {
        filterChain.doFilter(servletRequest, servletResponse);
      }
    }

    @Override
    public void destroy() {}
  }

  public static class DefaultServlet extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
      throw new IllegalStateException(
          "Servlet should not have been called, filter should have handled the request.");
    }
  }
}
