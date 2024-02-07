/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finagle.v23_11;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.CONNECTION_TIMEOUT;
import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.READ_TIMEOUT;

import com.twitter.finagle.Http;
import com.twitter.finagle.Service;
import com.twitter.finagle.http.Method;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.finagle.service.RetryBudget;
import com.twitter.util.Await;
import com.twitter.util.Duration;
import com.twitter.util.Time;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class FinagleClientExtension implements BeforeEachCallback, AfterEachCallback {
  private Map<URI, Service<Request, Response>> clients;

  private final Function<Http.Client, Http.Client> clientBuilder;

  public FinagleClientExtension(Function<Http.Client, Http.Client> clientBuilder) {
    this.clientBuilder = clientBuilder;
  }

  public static FinagleClientExtension http_1() {
    return new FinagleClientExtension(Http.Client::withNoHttp2);
  }

  // todo implement http/2-specific tests
  //    .withHttp2()
  //    .configured(PriorKnowledge.apply(true).mk())
  public static FinagleClientExtension http_2() {
    return new FinagleClientExtension(Http.Client::withHttp2);
  }

  static int safePort(URI uri) {
    int port = uri.getPort();
    if (port == -1) {
      port = uri.getScheme().equals("https") ? 443 : 80;
    }
    return port;
  }

  static Request buildRequest(String method, URI uri, Map<String, String> headers) {
    Request request =
        Request.apply(
            Method.apply(method.toUpperCase(Locale.ENGLISH)),
            uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getRawQuery()));
    request.host(uri.getHost() + ":" + safePort(uri));
    headers.forEach((key, value) -> request.headerMap().put(key, value));
    return request;
  }

  public Http.Client builderFor(URI uri) {
    Http.Client client =
        clientBuilder
            .apply(Http.client())
            .withTransport()
            .readTimeout(Duration.fromMilliseconds(READ_TIMEOUT.toMillis()))
            .withTransport()
            .connectTimeout(Duration.fromMilliseconds(CONNECTION_TIMEOUT.toMillis()))
            // disable automatic retries -- retries will result in under-counting traces in the
            // tests
            .withRetryBudget(RetryBudget.Empty());

    if (uri.getScheme().equals("https")) {
      client = client.withTransport().tlsWithoutValidation();
    }

    return client;
  }

  public Service<Request, Response> clientFor(URI uri) {
    return clients.computeIfAbsent(
        uri, ignored -> builderFor(uri).newService(uri.getHost() + ":" + safePort(uri)));
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    clients = new ConcurrentHashMap<>();
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    for (Service<Request, Response> client : clients.values()) {
      Await.ready(client.close(Time.fromSeconds(10)));
    }
  }
}
