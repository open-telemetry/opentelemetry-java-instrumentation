/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Collections.emptySet;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class Netty38ClientTest extends AbstractHttpClientTest<Request> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  AsyncHttpClient client;

  @BeforeEach
  void setUp() throws Exception {
    AsyncHttpClientConfig.Builder builder =
        new AsyncHttpClientConfig.Builder().setUserAgent("test-user-agent");

    Method setConnectTimeout;
    try {
      setConnectTimeout =
          AsyncHttpClientConfig.Builder.class.getMethod("setConnectTimeout", int.class);
    } catch (NoSuchMethodException e) {
      setConnectTimeout =
          AsyncHttpClientConfig.Builder.class.getMethod("setRequestTimeoutInMs", int.class);
    }
    setConnectTimeout.invoke(builder, (int) CONNECTION_TIMEOUT.toMillis());

    Method setFollowRedirect;
    try {
      setFollowRedirect =
          AsyncHttpClientConfig.Builder.class.getMethod("setFollowRedirect", boolean.class);
    } catch (NoSuchMethodException e) {
      setFollowRedirect =
          AsyncHttpClientConfig.Builder.class.getMethod("setFollowRedirects", boolean.class);
    }
    setFollowRedirect.invoke(builder, true);

    Method setMaxRedirects;
    try {
      setMaxRedirects = AsyncHttpClientConfig.Builder.class.getMethod("setMaxRedirects", int.class);
    } catch (NoSuchMethodException e) {
      setMaxRedirects =
          AsyncHttpClientConfig.Builder.class.getMethod("setMaximumNumberOfRedirects", int.class);
    }
    setMaxRedirects.invoke(builder, 3);

    Method setAllowPoolingConnections;
    try {
      setAllowPoolingConnections =
          AsyncHttpClientConfig.Builder.class.getMethod(
              "setAllowPoolingConnections", boolean.class);
    } catch (NoSuchMethodException e) {
      setAllowPoolingConnections =
          AsyncHttpClientConfig.Builder.class.getMethod("setAllowPoolingConnection", boolean.class);
    }
    setAllowPoolingConnections.invoke(builder, false);

    client = new AsyncHttpClient(builder.build());
  }

  @Override
  public Request buildRequest(String method, URI uri, Map<String, String> headers) {
    RequestBuilder requestBuilder = new RequestBuilder(method).setUrl(uri.toString());
    headers.forEach(requestBuilder::addHeader);
    return requestBuilder.build();
  }

  @Override
  public int sendRequest(Request request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    return client.executeRequest(request).get().getStatusCode();
  }

  @Override
  public void sendRequestWithCallback(
      Request request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult)
      throws Exception {

    // TODO: context is not automatically propagated into callbacks
    Context context = Context.current();
    // TODO: Do we also need to test ListenableFuture callback?
    client.executeRequest(
        request,
        new AsyncCompletionHandler<Void>() {
          @Override
          public Void onCompleted(Response response) {
            try (Scope ignored = context.makeCurrent()) {
              httpClientResult.complete(response.getStatusCode());
            }
            return null;
          }

          @Override
          public void onThrowable(Throwable throwable) {
            try (Scope ignored = context.makeCurrent()) {
              httpClientResult.complete(throwable);
            }
          }
        });
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.disableTestRedirects();
    optionsBuilder.disableTestHttps();
    optionsBuilder.disableTestReadTimeout();

    optionsBuilder.setExpectedClientSpanNameMapper(
        (uri, method) -> {
          // unopened port or non routable address
          if ("http://localhost:61/".equals(uri.toString())
              || "http://192.0.2.1/".equals(uri.toString())) {
            return "CONNECT";
          }
          return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(uri, method);
        });

    optionsBuilder.setClientSpanErrorMapper(
        (uri, error) -> {
          if ("http://localhost:61/".equals(uri.toString())) { // unopened port
            error =
                error.getCause() != null
                    ? error.getCause()
                    : new ConnectException("Connection refused: localhost/127.0.0.1:61");
          } else if ("http://192.0.2.1/".equals(uri.toString())) { // non routable address
            error = error.getCause() != null ? error.getCause() : new ClosedChannelException();
          }
          return error;
        });

    optionsBuilder.setHttpAttributes(
        uri -> {
          // unopened port or non routable address
          if ("http://localhost:61/".equals(uri.toString())
              || "http://192.0.2.1/".equals(uri.toString())) {
            return emptySet();
          }
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(SERVER_ADDRESS);
          attributes.remove(SERVER_PORT);
          return attributes;
        });
  }
}
