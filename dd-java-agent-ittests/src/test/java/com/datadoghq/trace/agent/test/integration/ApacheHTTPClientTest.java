package com.datadoghq.trace.agent.test.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;

public class ApacheHTTPClientTest {

  @Test
  public void test() throws Exception {
    // Since the HttpClientBuilder initializer doesn't work, invoke manually.
    Class.forName("com.datadoghq.trace.agent.InstrumentationRulesManager")
        .getMethod("registerClassLoad")
        .invoke(null);

    final HttpClientBuilder builder = HttpClientBuilder.create();
    assertThat(builder.getClass().getSimpleName()).isEqualTo("TracingHttpClientBuilder");
  }
}
