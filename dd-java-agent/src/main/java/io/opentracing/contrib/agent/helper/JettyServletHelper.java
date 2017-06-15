package io.opentracing.contrib.agent.helper;

import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.jboss.byteman.rule.Rule;

import javax.servlet.Filter;
import java.util.EnumSet;

/**
 * Created by gpolaert on 6/15/17.
 */
public class JettyServletHelper extends DDTracingHelper<ServletContextHandler> {

	public JettyServletHelper(Rule rule) {
		super(rule);
	}

	@Override
	protected ServletContextHandler doPatch(ServletContextHandler contextHandler) throws Exception {

		String[] patterns = {"/*"};

		Filter filter = new TracingFilter(tracer);
		contextHandler
				.getServletContext()
				.addFilter("tracingFilter", filter)
				.addMappingForUrlPatterns(EnumSet.allOf(javax.servlet.DispatcherType.class), true, patterns);

		setState(contextHandler.getServletContext(), 1);
		return contextHandler;
	}
}

