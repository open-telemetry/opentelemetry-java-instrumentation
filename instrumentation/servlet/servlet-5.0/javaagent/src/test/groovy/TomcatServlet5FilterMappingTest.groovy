/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.tomcat.util.descriptor.web.FilterDef
import org.apache.tomcat.util.descriptor.web.FilterMap

abstract class TomcatServlet5FilterMappingTest extends TomcatServlet5MappingTest {

  void addFilter(Context servletContext, String path, Class<Filter> filter) {
    String name = UUID.randomUUID()
    FilterDef filterDef = new FilterDef()
    filterDef.setFilter(filter.newInstance())
    filterDef.setFilterName(name)
    servletContext.addFilterDef(filterDef)
    FilterMap filterMap = new FilterMap()
    filterMap.setFilterName(name)
    filterMap.addURLPattern(path)
    servletContext.addFilterMap(filterMap)
  }

  void addFilterWithServletName(Context servletContext, String servletName, Class<Filter> filter) {
    String name = UUID.randomUUID()
    FilterDef filterDef = new FilterDef()
    filterDef.setFilter(filter.newInstance())
    filterDef.setFilterName(name)
    servletContext.addFilterDef(filterDef)
    FilterMap filterMap = new FilterMap()
    filterMap.setFilterName(name)
    filterMap.addServletName(servletName)
    servletContext.addFilterMap(filterMap)
  }

  static class TestFilter implements Filter {
    @Override
    void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
      if (servletRequest.getAttribute("firstFilterCalled") != null) {
        servletRequest.setAttribute("testFilterCalled", Boolean.TRUE)
        filterChain.doFilter(servletRequest, servletResponse)
      } else {
        throw new IllegalStateException("First filter should have been called.")
      }
    }

    @Override
    void destroy() {
    }
  }

  static class FirstFilter implements Filter {
    @Override
    void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
      servletRequest.setAttribute("firstFilterCalled", Boolean.TRUE)
      filterChain.doFilter(servletRequest, servletResponse)
    }

    @Override
    void destroy() {
    }
  }

  static class LastFilter implements Filter {

    @Override
    void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
      if (servletRequest.getAttribute("testFilterCalled") != null) {
        HttpServletResponse response = (HttpServletResponse) servletResponse
        response.getWriter().write("Ok")
        response.setStatus(HttpServletResponse.SC_OK)
      } else {
        filterChain.doFilter(servletRequest, servletResponse)
      }
    }

    @Override
    void destroy() {
    }
  }

  static class DefaultServlet extends HttpServlet {
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
      throw new IllegalStateException("Servlet should not have been called, filter should have handled the request.")
    }
  }
}

class TomcatServlet5FilterUrlPatternMappingTest extends TomcatServlet5FilterMappingTest {
  @Override
  protected void setupServlets(Context context) {
    addFilter(context, "/*", FirstFilter)
    addFilter(context, "/prefix/*", TestFilter)
    addFilter(context, "*.suffix", TestFilter)
    addFilter(context, "/*", LastFilter)
  }
}

class TomcatServlet5FilterServletNameMappingTest extends TomcatServlet5FilterMappingTest {
  @Override
  protected void setupServlets(Context context) {
    Tomcat.addServlet(context, "prefix-servlet", DefaultServlet.newInstance())
    context.addServletMappingDecoded("/prefix/*", "prefix-servlet")
    Tomcat.addServlet(context, "suffix-servlet", DefaultServlet.newInstance())
    context.addServletMappingDecoded("*.suffix", "suffix-servlet")

    addFilter(context, "/*", FirstFilter)
    addFilterWithServletName(context, "prefix-servlet", TestFilter)
    addFilterWithServletName(context, "suffix-servlet", TestFilter)
    addFilterWithServletName(context, "prefix-servlet", LastFilter)
    addFilterWithServletName(context, "suffix-servlet", LastFilter)
  }
}
