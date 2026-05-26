/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter;
import com.sun.jersey.api.client.filter.LoggingFilter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.extension.RegisterExtension;

class JaxRsClientV1Test extends AbstractHttpClientTest<WebResource.Builder> {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final Client client = buildClient(false);
  private final Client clientWithReadTimeout = buildClient(true);

  private static Client buildClient(boolean readTimeout) {
    Client client = Client.create();
    client.setConnectTimeout((int) CONNECTION_TIMEOUT.toMillis());
    if (readTimeout) {
      client.setReadTimeout((int) READ_TIMEOUT.toMillis());
    }
    // Add filters to ensure spans aren't duplicated.
    client.addFilter(new LoggingFilter());
    client.addFilter(new GZIPContentEncodingFilter());

    // shared across tests via @TestInstance(PER_CLASS), destroy after the class finishes
    cleanup.deferAfterAll(client::destroy);
    return client;
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder options) {
    options.setTestCircularRedirects(false);
    options.setTestNonStandardHttpMethod(false);
    options.setTestCallback(false);
    options.setHttpAttributes(
        serverEndpoint -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(NETWORK_PROTOCOL_VERSION);
          return attributes;
        });
  }

  private Client getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
  }

  @Override
  public WebResource.Builder buildRequest(String method, URI uri, Map<String, String> headers) {
    WebResource.Builder builder = getClient(uri).resource(uri).getRequestBuilder();
    headers.forEach(builder::header);
    return builder;
  }

  @Override
  public int sendRequest(
      WebResource.Builder builder, String method, URI uri, Map<String, String> headers)
      throws Exception {
    String body = method.equals("POST") || method.equals("PUT") ? "" : null;
    try {
      return builder.method(method, ClientResponse.class, body).getStatus();
    } catch (ClientHandlerException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      throw new IllegalStateException(cause);
    }
  }
}
