package com.datadoghq.agent.integration;

import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import java.util.EnumSet;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import org.apache.catalina.core.ApplicationContext;
import org.jboss.byteman.rule.Rule;

/** Patch the Tomcat Servlet during the init steps */
public class TomcatServletHelper extends DDAgentTracingHelper<ApplicationContext> {

  private static final String pattern = "/*";

  public TomcatServletHelper(final Rule rule) {
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
  protected ApplicationContext doPatch(final ApplicationContext contextHandler) throws Exception {
    if (contextHandler.getFilterRegistration("tracingFilter") == null) {
      final Filter filter = new TracingFilter(tracer);
      final FilterRegistration.Dynamic registration =
          contextHandler.addFilter("tracingFilter", filter);
      if (registration != null) { // filter of that name must already be registered.
        registration.setAsyncSupported(true);
        registration.addMappingForUrlPatterns(
            EnumSet.allOf(javax.servlet.DispatcherType.class), true, pattern);
      }
    }
    return contextHandler;
  }
}
