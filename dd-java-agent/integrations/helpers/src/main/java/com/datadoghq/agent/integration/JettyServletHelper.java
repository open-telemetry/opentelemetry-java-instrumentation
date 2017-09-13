package com.datadoghq.agent.integration;

import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import java.util.EnumSet;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.jboss.byteman.rule.Rule;

/** Patch the Jetty Servlet during the init steps */
public class JettyServletHelper extends DDAgentTracingHelper<ServletContextHandler> {

  private static final String pattern = "/*";

  public JettyServletHelper(final Rule rule) {
    super(rule);
  }

  /**
   * Strategy: Use the contextHandler provided to add a new Tracing filter
   *
   * @param contextHandler The current contextHandler
   * @return The same current contextHandler but patched
   * @throws Exception
   */
  @Override
  protected ServletContextHandler doPatch(final ServletContextHandler contextHandler)
      throws Exception {
    if (contextHandler.getServletContext().getFilterRegistration("tracingFilter") == null) {
      final Filter filter = new TracingFilter(tracer);
      final FilterRegistration.Dynamic registration =
          contextHandler.getServletContext().addFilter("tracingFilter", filter);
      if (registration != null) { // filter of that name must already be registered.
        registration.setAsyncSupported(true);
        registration.addMappingForUrlPatterns(
            EnumSet.allOf(javax.servlet.DispatcherType.class), true, pattern);
      }
    }
    return contextHandler;
  }
}
