/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient;

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
}
