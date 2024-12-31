/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient;

import static java.util.Arrays.asList;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class AbstractJaxRsClientTest extends AbstractHttpClientTest<Invocation.Builder> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  protected static final List<String> BODY_METHODS = asList("POST", "PUT");

  @Override
  public Invocation.Builder buildRequest(String method, URI uri, Map<String, String> headers) {
    Client client = builder(uri).build();
    WebTarget service = client.target(uri);
    Invocation.Builder requestBuilder = service.request(MediaType.TEXT_PLAIN);
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      requestBuilder.header(entry.getKey(), entry.getValue());
    }
    return requestBuilder;
  }

  abstract ClientBuilder builder(URI uri);

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);
    optionsBuilder.setTestRedirects(false);
    optionsBuilder.setTestNonStandardHttpMethod(false);
  }

  @Override
  public int sendRequest(
      Invocation.Builder request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    try {
      Entity<String> body = BODY_METHODS.contains(method) ? Entity.text("") : null;
      Response response = request.build(method, body).invoke();
      // read response body to avoid broken pipe errors on the server side
      response.readEntity(String.class);
      response.close();
      return response.getStatus();
    } catch (ProcessingException exception) {
      if (exception.getCause() instanceof Exception) {
        throw (Exception) exception.getCause();
      }
      throw exception;
    }
  }

  @Override
  public void sendRequestWithCallback(
      Invocation.Builder request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult requestResult) {
    Entity<String> body = BODY_METHODS.contains(method) ? Entity.text("") : null;

    request
        .async()
        .method(
            method,
            body,
            new InvocationCallback<Response>() {
              @Override
              public void completed(Response response) {
                // read response body
                response.readEntity(String.class);
                requestResult.complete(response.getStatus());
              }

              @Override
              public void failed(Throwable throwable) {
                if (throwable instanceof ProcessingException) {
                  throwable = throwable.getCause();
                }
                requestResult.complete(throwable);
              }
            });
  }
}
