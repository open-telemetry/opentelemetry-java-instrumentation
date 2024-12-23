/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient;

import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.ClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

class ResteasyClientTest extends AbstractJaxRsClientTest {

  @Override
  ClientBuilder builder() {
    return new ResteasyClientBuilder()
        .establishConnectionTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .socketTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);
    optionsBuilder.setSingleConnectionFactory(ResteasySingleConnection::new);
  }
}
