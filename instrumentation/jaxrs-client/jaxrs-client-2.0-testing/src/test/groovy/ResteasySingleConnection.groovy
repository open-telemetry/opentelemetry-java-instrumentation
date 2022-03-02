/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection
import org.jboss.resteasy.client.jaxrs.ResteasyClient
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder

import javax.ws.rs.core.MediaType
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ResteasySingleConnection implements SingleConnection {
  private final ResteasyClient client
  private final String host
  private final int port

  ResteasySingleConnection(String host, int port) {
    this.host = host
    this.port = port
    this.client = new ResteasyClientBuilder()
      .establishConnectionTimeout(5000, TimeUnit.MILLISECONDS)
      .connectionPoolSize(1)
      .build()
  }

  @Override
  int doRequest(String path, Map<String, String> headers) throws ExecutionException, InterruptedException, TimeoutException {
    String requestId = Objects.requireNonNull(headers.get(REQUEST_ID_HEADER))

    URI uri
    try {
      uri = new URL("http", host, port, path).toURI()
    } catch (MalformedURLException e) {
      throw new ExecutionException(e)
    }

    def requestBuilder = client.target(uri).request(MediaType.TEXT_PLAIN)
    headers.each { requestBuilder.header(it.key, it.value) }

    def response = requestBuilder.buildGet().invoke()
    response.close()

    String responseId = response.getHeaderString(REQUEST_ID_HEADER)
    if (requestId != responseId) {
      throw new IllegalStateException(
        String.format("Received response with id %s, expected %s", responseId, requestId))
    }

    return response.getStatus()
  }
}
