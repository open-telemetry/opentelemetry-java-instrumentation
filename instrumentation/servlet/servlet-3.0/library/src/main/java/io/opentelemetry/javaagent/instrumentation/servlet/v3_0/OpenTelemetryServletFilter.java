package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.helper;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * OpenTelemetry Library instrumentation for Java Servlet based applications that can't use a Java
 * Agent. Due to inherit limitations in the servlet filter API, instrumenting at the filter level
 * will miss anything that happens earlier in the filter stack or problems handled directly by the
 * app server. For this reason, Java Agent instrumentation is preferred when possible.
 */
public class OpenTelemetryServletFilter implements Filter {

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

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
    ServletRequestContext<HttpServletRequest> requestContext =
        new ServletRequestContext<>(httpRequest, this);

    // Bail if we shouldn't start a new span.
    if (!helper().shouldStart(Context.current(), requestContext)) {
      chain.doFilter(request, response);
      return;
    }

    Context spanContext = helper().start(Context.current(), requestContext);
    helper().setAsyncListenerResponse(spanContext, (HttpServletResponse) response);

    // Not using try-with-resources to match the api usage of Servlet3Advice.
    // (helper().end is responsible for closing the scope.)
    Scope scope = spanContext.makeCurrent();
    Throwable throwable = null;
    try {
      chain.doFilter(
          new OtelHttpServletRequest((HttpServletRequest) request), response);
    } catch (Throwable e) {
      throwable = e;
      throw e;
    } finally {
      helper().end(requestContext, httpRequest, httpResponse, throwable, true, spanContext, scope);
    }
  }

  @Override
  public void destroy() {}
}
