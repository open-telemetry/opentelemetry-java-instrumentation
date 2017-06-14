package io.opentracing.contrib.agent.helper;

import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.jboss.byteman.rule.Rule;


public class ElasticsearchHelper extends DDTracingHelper<PreBuiltTransportClient> {


	public ElasticsearchHelper(Rule rule) {
		super(rule);
	}


	public PreBuiltTransportClient patch(PreBuiltTransportClient client) {
		return super.patch(client);
	}


	@Override
	protected PreBuiltTransportClient doPatch(PreBuiltTransportClient client) throws Exception {

		return client;
	}

}