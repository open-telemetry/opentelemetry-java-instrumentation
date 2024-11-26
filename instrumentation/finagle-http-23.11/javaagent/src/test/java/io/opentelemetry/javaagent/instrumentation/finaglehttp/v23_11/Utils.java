/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.CONNECTION_TIMEOUT;
import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.READ_TIMEOUT;

import com.twitter.finagle.Http;
import com.twitter.finagle.http.Method;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.service.RetryBudget;
import com.twitter.util.Duration;
import java.net.URI;
import java.util.Locale;
import java.util.Map;

final class Utils {

  private Utils() {}

  static Http.Client createClient(ClientType clientType) {
    Http.Client client =
        Http.client()
            .withNoHttp2()
            .withTransport()
            .readTimeout(Duration.fromMilliseconds(READ_TIMEOUT.toMillis()))
            .withTransport()
            .connectTimeout(Duration.fromMilliseconds(CONNECTION_TIMEOUT.toMillis()))
            // disable automatic retries -- retries will result in under-counting traces in the
            // tests
            .withRetryBudget(RetryBudget.Empty());

    switch (clientType) {
      case TLS:
        client = client.withTransport().tlsWithoutValidation();
        break;
      case SINGLE_CONN:
        client = client.withSessionPool().maxSize(1);
        break;
      case DEFAULT:
        break;
    }

    return client;
  }

  enum ClientType {
    TLS,
    SINGLE_CONN,
    DEFAULT;
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
}
