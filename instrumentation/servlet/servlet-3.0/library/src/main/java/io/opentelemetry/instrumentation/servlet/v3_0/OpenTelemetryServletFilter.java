package io.opentelemetry.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.FILTER_MAPPING_RESOLVER;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3FilterMappingResolverFactory;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3RequestAdviceScope;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * OpenTelemetry Library instrumentation for Java Servlet based applications that can't use a Java
 * Agent. Due to inherit limitations in the servlet filter API, instrumenting at the filter level
 * will miss anything that happens earlier in the filter stack or problems handled directly by the
 * app server. For this reason, Java Agent instrumentation is preferred when possible.
 */
 @WebFilter("/*")
public class OpenTelemetryServletFilter implements Filter {

  @Override
  public void init(FilterConfig filterConfig) {
    FILTER_MAPPING_RESOLVER.set(this, new Servlet3FilterMappingResolverFactory(filterConfig));
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    // Only HttpServlets are supported.
    if (!(request instanceof HttpServletRequest && response instanceof HttpServletResponse)) {
      chain.doFilter(request, response);
      return;
    }

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    Throwable throwable = null;
    Servlet3RequestAdviceScope adviceScope =
        new Servlet3RequestAdviceScope(
            CallDepth.forClass(OpenTelemetryServletFilter.class), httpRequest, httpResponse, this);
    try {
      chain.doFilter(
          new OtelHttpServletRequest(httpRequest), new OtelHttpServletResponse(httpResponse));
    } catch (Throwable e) {
      throwable = e;
      throw e;
    } finally {
      adviceScope.exit(throwable, httpRequest, httpResponse);
    }
  }

  @Override
  public void destroy() {}
}
