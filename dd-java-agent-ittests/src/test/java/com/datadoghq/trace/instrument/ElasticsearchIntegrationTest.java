package com.datadoghq.trace.instrument;

import io.opentracing.contrib.elasticsearch.TracingPreBuiltTransportClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ElasticsearchIntegrationTest {


	@Test
	public void test() {


		TransportClient builder = new PreBuiltTransportClient(Settings.EMPTY);
		assertThat(builder).isInstanceOf(TracingPreBuiltTransportClient.class);
	}

}
