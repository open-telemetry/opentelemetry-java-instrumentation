package com.datadoghq.agent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;

public class ApacheHTTPClientTest {
  static {
    try {
      // Since the HttpClientBuilder initializer doesn't work, invoke manually.
      Class.forName("com.datadoghq.agent.InstrumentationRulesManager")
          .getMethod("registerClassLoad", Object.class)
          .invoke(null, Thread.currentThread().getContextClassLoader());
    } catch (Exception e) {
      System.err.println("clinit error: " + e.getMessage());
    }
  }

  @Test
  public void test() throws Exception {
    final HttpClientBuilder builder = HttpClientBuilder.create();
    assertThat(builder.getClass().getSimpleName()).isEqualTo("TracingHttpClientBuilder");
  }
}
