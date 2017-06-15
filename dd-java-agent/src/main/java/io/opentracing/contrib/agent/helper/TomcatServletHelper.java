package io.opentracing.contrib.agent.helper;

import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import org.apache.catalina.core.ApplicationContext;
import org.jboss.byteman.rule.Rule;

import javax.servlet.Filter;
import java.util.EnumSet;

/**
 * Created by gpolaert on 6/15/17.
 */
public class TomcatServletHelper extends DDTracingHelper<ApplicationContext> {

	public TomcatServletHelper(Rule rule) {
		super(rule);
	}

	@Override
	protected ApplicationContext doPatch(ApplicationContext contextHandler) throws Exception {

		String[] patterns = {"/*"};

		Filter filter = new TracingFilter(tracer);
		contextHandler
				.addFilter("tracingFilter", filter)
				.addMappingForUrlPatterns(EnumSet.allOf(javax.servlet.DispatcherType.class), true, patterns);

		return contextHandler;
	}
}
