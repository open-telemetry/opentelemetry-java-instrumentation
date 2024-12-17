/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient;

import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import javax.ws.rs.client.ClientBuilder;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClientBuilder;

class JerseyClientTest extends JaxRsClientTest {

  @Override
  public ClientBuilder builder() {
    ClientConfig config = new ClientConfig();
    config.property(ClientProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT_MS);
    config.property(ClientProperties.READ_TIMEOUT, READ_TIMEOUT_MS);
    return new JerseyClientBuilder().withConfig(config);
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);
    // Jersey JAX-RS client uses HttpURLConnection internally, which does not support pipelining nor
    // waiting for a connection in the pool to become available. Therefore a high concurrency test
    // would require manually doing requests one after another which is not meaningful for a high
    // concurrency test.
    optionsBuilder.setSingleConnectionFactory((host, port) -> null);
  }
}
