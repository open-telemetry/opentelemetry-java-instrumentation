/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.tomcat.util.descriptor.web.FilterDef
import org.apache.tomcat.util.descriptor.web.FilterMap

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

abstract class TomcatServlet3FilterMappingTest extends TomcatServlet3MappingTest {

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

class TomcatServlet3FilterUrlPatternMappingTest extends TomcatServlet3FilterMappingTest {
  @Override
  protected void setupServlets(Context context) {
    addFilter(context, "/*", FirstFilter)
    addFilter(context, "/prefix/*", TestFilter)
    addFilter(context, "*.suffix", TestFilter)
    addFilter(context, "/*", LastFilter)
  }
}

class TomcatServlet3FilterServletNameMappingTest extends TomcatServlet3FilterMappingTest {
  @Override
  protected void setupServlets(Context context) {
    Tomcat.addServlet(context, "prefix-servlet", new DefaultServlet())
    context.addServletMappingDecoded("/prefix/*", "prefix-servlet")
    Tomcat.addServlet(context, "suffix-servlet", new DefaultServlet())
    context.addServletMappingDecoded("*.suffix", "suffix-servlet")

    addFilter(context, "/*", FirstFilter)
    addFilterWithServletName(context, "prefix-servlet", TestFilter)
    addFilterWithServletName(context, "suffix-servlet", TestFilter)
    addFilterWithServletName(context, "prefix-servlet", LastFilter)
    addFilterWithServletName(context, "suffix-servlet", LastFilter)
  }
}
