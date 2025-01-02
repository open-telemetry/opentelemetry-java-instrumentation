/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient;

import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.ClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

class ResteasyClientTest extends AbstractJaxRsClientTest {

  @Override
  ClientBuilder builder(URI uri) {
    ResteasyClientBuilder builder =
        new ResteasyClientBuilder()
            .establishConnectionTimeout(CONNECTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    if (uri.toString().contains("/read-timeout")) {
      builder.socketTimeout(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }
    return builder;
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);
    optionsBuilder.setSingleConnectionFactory(ResteasySingleConnection::new);
  }
}
