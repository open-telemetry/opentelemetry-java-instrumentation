/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient;

import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

class ResteasySingleConnection implements SingleConnection {
  private final ResteasyClient client;
  private final String host;
  private final int port;

  ResteasySingleConnection(String host, int port) {
    this.host = host;
    this.port = port;
    this.client =
        new ResteasyClientBuilder()
            .establishConnectionTimeout(5000, TimeUnit.MILLISECONDS)
            .connectionPoolSize(1)
            .build();
  }

  @Override
  public int doRequest(String path, Map<String, String> headers) throws ExecutionException {
    String requestId = Objects.requireNonNull(headers.get(REQUEST_ID_HEADER));

    URI uri;
    try {
      uri = new URL("http", host, port, path).toURI();
    } catch (MalformedURLException | URISyntaxException e) {
      throw new ExecutionException(e);
    }

    Invocation.Builder requestBuilder = client.target(uri).request(MediaType.TEXT_PLAIN);
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      requestBuilder.header(entry.getKey(), entry.getValue());
    }

    Response response = requestBuilder.buildGet().invoke();
    response.close();

    String responseId = response.getHeaderString(REQUEST_ID_HEADER);
    if (Objects.equals(requestId, responseId)) {
      throw new IllegalStateException(
          String.format("Received response with id %s, expected %s", responseId, requestId));
    }

    return response.getStatus();
  }
}
