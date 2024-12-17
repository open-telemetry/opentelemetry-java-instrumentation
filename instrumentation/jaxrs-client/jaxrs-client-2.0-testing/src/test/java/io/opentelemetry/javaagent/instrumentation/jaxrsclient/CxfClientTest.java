/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient;

import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.ConnectException;
import java.net.URI;
import javax.ws.rs.client.ClientBuilder;
import org.apache.cxf.jaxrs.client.spec.ClientBuilderImpl;

class CxfClientTest extends JaxRsClientTest {

  @Override
  public ClientBuilder builder() {
    return new ClientBuilderImpl()
        .property("http.connection.timeout", (long) CONNECT_TIMEOUT_MS)
        .property("org.apache.cxf.transport.http.forceVersion", "1.1");
  }

  Throwable clientSpanError(URI uri, Throwable exception) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
        if (exception.getCause() instanceof ConnectException) {
          exception = exception.getCause();
        }
        break;
      case "https://192.0.2.1/": // non routable address
        if (exception.getCause() != null) {
          exception = exception.getCause();
        }
        break;
      default:
        break;
    }
    return exception;
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);
    optionsBuilder.setTestReadTimeout(false);
    optionsBuilder.setTestWithClientParent(!Boolean.getBoolean("testLatestDeps"));
    optionsBuilder.setClientSpanErrorMapper((uri, exception) -> clientSpanError(uri, exception));
    // CXF JAX-RS client uses HttpURLConnection internally, which does not support pipelining nor
    // waiting for a connection in the pool to become available. Therefore a high concurrency test
    // would require manually doing requests one after another which is not meaningful for a high
    // concurrency test.
    optionsBuilder.setSingleConnectionFactory((host, port) -> null);
  }
}
