/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.http.HttpMethod;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

public class OkHttp2Test extends AbstractHttpClientTest<Request> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private static final OkHttpClient client = new OkHttpClient();
  private static final OkHttpClient clientWithReadTimeout = new OkHttpClient();

  @BeforeAll
  void setup() {
    client.setConnectTimeout(CONNECTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    clientWithReadTimeout.setConnectTimeout(CONNECTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    clientWithReadTimeout.setReadTimeout(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public Request buildRequest(String method, URI uri, Map<String, String> headers)
      throws Exception {
    RequestBody body =
        HttpMethod.requiresRequestBody(method)
            ? RequestBody.create(MediaType.parse("text/plain"), "")
            : null;
    Request.Builder request = new Request.Builder().url(uri.toURL()).method(method, body);
    headers.forEach(request::header);
    return request.build();
  }

  @Override
  public int sendRequest(Request request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    return getClient(uri).newCall(request).execute().code();
  }

  @Override
  public void sendRequestWithCallback(
      Request request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    getClient(uri)
        .newCall(request)
        .enqueue(
            new Callback() {
              @Override
              public void onFailure(Request req, IOException e) {
                httpClientResult.complete(e);
              }

              @Override
              public void onResponse(Response response) {
                httpClientResult.complete(response.code());
              }
            });
  }

  private static OkHttpClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.disableTestCircularRedirects();

    optionsBuilder.setHttpAttributes(
        uri -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          // protocol is extracted from the response, and those URLs cause exceptions (= null
          // response)
          if ("http://localhost:61/".equals(uri.toString())
              || "https://192.0.2.1/".equals(uri.toString())
              || resolveAddress("/read-timeout").toString().equals(uri.toString())) {
            attributes.remove(NETWORK_PROTOCOL_VERSION);
          }
          return attributes;
        });
  }
}
