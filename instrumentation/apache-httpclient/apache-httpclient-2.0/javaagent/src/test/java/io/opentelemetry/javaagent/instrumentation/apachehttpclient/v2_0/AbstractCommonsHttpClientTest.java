/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.Map;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.OptionsMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.TraceMethod;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class AbstractCommonsHttpClientTest extends AbstractHttpClientTest<HttpMethod> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private static final HttpConnectionManager connectionManager =
      new MultiThreadedHttpConnectionManager();
  private static final HttpClient client = buildClient(false);
  private static final HttpClient clientWithReadTimeout = buildClient(true);

  static HttpClient buildClient(boolean readTimeout) {
    HttpClient client = new HttpClient(connectionManager);
    client.setConnectionTimeout((int) CONNECTION_TIMEOUT.toMillis());
    if (readTimeout) {
      client.setTimeout((int) READ_TIMEOUT.toMillis());
    }
    return client;
  }

  HttpClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
  }

  @Override
  public HttpMethod buildRequest(String method, URI uri, Map<String, String> headers) {
    HttpMethod request;
    switch (method) {
      case "GET":
        request = new GetMethod(uri.toString());
        break;
      case "PUT":
        request = new PutMethod(uri.toString());
        break;
      case "POST":
        request = new PostMethod(uri.toString());
        break;
      case "HEAD":
        request = new HeadMethod(uri.toString());
        break;
      case "DELETE":
        request = new DeleteMethod(uri.toString());
        break;
      case "OPTIONS":
        request = new OptionsMethod(uri.toString());
        break;
      case "TRACE":
        request = new TraceMethod(uri.toString());
        break;
      default:
        throw new IllegalStateException("Unsupported method: " + method);
    }

    for (Map.Entry<String, String> entry : headers.entrySet()) {
      request.setRequestHeader(entry.getKey(), entry.getValue());
    }
    return request;
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder
        .disableTestCallback()
        .disableTestReusedRequest()
        .disableTestNonStandardHttpMethod()
        .disableTestCircularRedirects();
  }
}
