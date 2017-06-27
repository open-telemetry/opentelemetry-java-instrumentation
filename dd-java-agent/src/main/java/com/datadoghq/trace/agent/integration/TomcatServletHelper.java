package com.datadoghq.trace.agent.integration;

import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import org.apache.catalina.core.ApplicationContext;
import org.jboss.byteman.rule.Rule;

import javax.servlet.Filter;
import java.util.EnumSet;

/**
 * Patch the Jetty Servlet during the init steps
 */
public class TomcatServletHelper extends DDAgentTracingHelper<ApplicationContext> {

	public TomcatServletHelper(Rule rule) {
		super(rule);
	}

	/**
	 * Strategy: Use the contextHandler provided to add a new Tracing filter
	 *
	 * @param contextHandler The current contextHandler
	 * @return The same current contextHandler but patched
	 * @throws Exception
	 */
	protected ApplicationContext doPatch(ApplicationContext contextHandler) throws Exception {

		String[] patterns = {"/*"};

		Filter filter = new TracingFilter(tracer);
		contextHandler
				.addFilter("tracingFilter", filter)
				.addMappingForUrlPatterns(EnumSet.allOf(javax.servlet.DispatcherType.class), true, patterns);

		return contextHandler;
	}
}
