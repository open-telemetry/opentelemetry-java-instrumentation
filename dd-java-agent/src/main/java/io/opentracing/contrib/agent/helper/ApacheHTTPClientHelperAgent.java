package io.opentracing.contrib.agent.helper;

import io.opentracing.contrib.apache.http.client.TracingHttpClientBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.byteman.rule.Rule;


public class ApacheHTTPClientHelperAgent extends DDAgentTracingHelper<HttpClientBuilder> {

	public ApacheHTTPClientHelperAgent(Rule rule) {
		super(rule);
	}


	public HttpClientBuilder patch(HttpClientBuilder builder) {
		return super.patch(builder);
	}


	@Override
	protected HttpClientBuilder doPatch(HttpClientBuilder builder) throws Exception {

		return new TracingHttpClientBuilder();

	}

}