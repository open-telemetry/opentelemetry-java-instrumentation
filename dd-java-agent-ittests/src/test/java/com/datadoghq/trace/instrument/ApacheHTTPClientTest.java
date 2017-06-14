package com.datadoghq.trace.instrument;

import io.opentracing.contrib.apache.http.client.TracingHttpClientBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static org.assertj.core.api.Assertions.assertThat;

public class ApacheHTTPClientTest extends AAgentIntegration {

	@Test
	public void test() throws Exception {

		HttpClientBuilder builder = HttpClientBuilder.create();
		assertThat(builder).isInstanceOf(TracingHttpClientBuilder.class);

		HttpClient client = builder.build();
		HttpGet request = new HttpGet("http://apache.org");

		// add request header
		HttpResponse response = client.execute(request);

		System.out.println("Response Code : "
				+ response.getStatusLine().getStatusCode());

		BufferedReader rd = new BufferedReader(
				new InputStreamReader(response.getEntity().getContent()));

		StringBuffer result = new StringBuffer();
		String line = "";
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}

		assertThat(writer.firstTrace().size()).isEqualTo(2);
	}

}
