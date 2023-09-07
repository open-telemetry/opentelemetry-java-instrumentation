/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractJettyClient9Test extends AbstractHttpClientTest<Request> {

  private static final String USER_AGENT = "Jetty";

  private HttpClient client;
  private HttpClient httpsClient;

  @BeforeEach
  public void before() throws Exception {
    // Start the main Jetty HttpClient and a https client
    client = createStandardClient();
    client.setConnectTimeout(5000L);
    client.start();

    SslContextFactory tlsCtx = new SslContextFactory();
    httpsClient = createHttpsClient(tlsCtx);
    httpsClient.setFollowRedirects(false);
    httpsClient.start();
  }

  @AfterEach
  public void after() throws Exception {
    client.stop();
    httpsClient.stop();
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.disableTestRedirects().setUserAgent(USER_AGENT);
  }

  @Override
  public Request buildRequest(String method, URI uri, Map<String, String> headers)
      throws Exception {
    HttpClient theClient = uri.getScheme().equalsIgnoreCase("https") ? httpsClient : client;
    Request request =
        theClient
            .newRequest(uri)
            .method(method)
            .agent(USER_AGENT)
            .timeout(5000L, TimeUnit.MILLISECONDS);
    headers.forEach(request::header);
    return request;
  }

  @Override
  public int sendRequest(Request httpRequest, String method, URI uri, Map<String, String> headers)
      throws ExecutionException, InterruptedException, TimeoutException {
    return httpRequest.send().getStatus();
  }

  @Override
  public void sendRequestWithCallback(
      Request request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult)
      throws Exception {
    JettyClientListener jcl = new JettyClientListener();
    request.onRequestFailure(jcl);
    request.onResponseFailure(jcl);
    headers.forEach(request::header);
    request.send(
        result -> {
          if (jcl.failure != null) {
            httpClientResult.complete(jcl.failure);
            return;
          }
          httpClientResult.complete(result.getResponse().getStatus());
        });
  }

  private static class JettyClientListener
      implements Request.FailureListener, Response.FailureListener {
    private volatile Throwable failure;

    @Override
    public void onFailure(Request requestF, Throwable failure) {
      this.failure = failure;
    }

    @Override
    public void onFailure(Response responseF, Throwable failure) {
      this.failure = failure;
    }
  }

  protected abstract HttpClient createStandardClient();

  protected abstract HttpClient createHttpsClient(SslContextFactory sslContextFactory);
}
