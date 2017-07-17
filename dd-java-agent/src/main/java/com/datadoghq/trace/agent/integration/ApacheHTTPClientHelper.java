package com.datadoghq.trace.agent.integration;

import io.opentracing.contrib.apache.http.client.TracingHttpClientBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.byteman.rule.Rule;

/** Patch the Apache HTTP Client during the building steps */
public class ApacheHTTPClientHelper extends DDAgentTracingHelper<HttpClientBuilder> {

  public ApacheHTTPClientHelper(Rule rule) {
    super(rule);
  }

  @Override
  public HttpClientBuilder patch(HttpClientBuilder builder) {
    return super.patch(builder);
  }

  /**
   * Strategy: We replace the legacy builder by a new instance providing by the opentracing
   * contribution when the builder is instantiate. @see
   * org.apache.http.impl.client.HttpClientBuilder.create() method
   *
   * @param builder The legacy builder instance
   * @return A tracing builder instance (new reference)
   * @throws Exception
   */
  protected HttpClientBuilder doPatch(HttpClientBuilder builder) throws Exception {
    return new TracingHttpClientBuilder();
  }
}
