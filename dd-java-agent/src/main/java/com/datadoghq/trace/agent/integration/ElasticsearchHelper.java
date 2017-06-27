package com.datadoghq.trace.agent.integration;

import io.opentracing.ActiveSpan;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.elasticsearch.TracingResponseListener;
import io.opentracing.tag.Tags;
import org.elasticsearch.action.ActionListener;
import org.jboss.byteman.rule.Rule;

import java.lang.reflect.Method;

/**
 * Instrument all Elasticsearch queries.
 * We have not found a way to inject the opentracing contribution, so this integration is the integration.
 * FIXME find a better to way to inject the OT contrib
 */
public class ElasticsearchHelper extends DDAgentTracingHelper<ActionListener> {

	public ElasticsearchHelper(Rule rule) {
		super(rule);
	}

	private Object request;

	/**
	 * This method is used to register/save some object that will be used for the integration.
	 * Currently, we need to keep a reference of the request called
	 *
	 * @param request The request used for the query
	 */
	public void registerArgs(Object request) {
		this.request = request;
	}

	@Override
	public ActionListener patch(ActionListener listener) {
		return super.patch(listener);
	}

	/**
	 * Strategy: When a query is executed, if start the integration and a new Span.
	 * We override the default FutureAction by using the one provided in the opentracing contribution.
	 *
	 * @param listener default listener
	 * @return The tracing listener, the default listener is wrapped in this one.
	 * @throws Exception
	 */
	protected ActionListener doPatch(ActionListener listener) throws Exception {

		if (listener instanceof TracingResponseListener) {
			return listener;
		}

		Tracer.SpanBuilder spanBuilder = tracer.buildSpan(request.getClass().getSimpleName()).ignoreActiveSpan().withTag(Tags.SPAN_KIND.getKey(), "client");
		ActiveSpan parentSpan = tracer.activeSpan();
		if (parentSpan != null) {
			spanBuilder.asChildOf(parentSpan.context());
		}

		Span span = spanBuilder.startManual();

		Class<?> clazz = Class.forName("io.opentracing.contrib.elasticsearch.SpanDecorator");
		Method method = clazz.getDeclaredMethod("onRequest", Span.class);
		method.setAccessible(true);
		method.invoke(null, span);

		ActionListener newListener = new TracingResponseListener(listener, span);
		return newListener;

	}
}