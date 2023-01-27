/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

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
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.InvocationTargetException;
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

  static final String USER_AGENT = "test-user-agent";

  AsyncHttpClient client;

  @BeforeEach
  void setUp() {
    AsyncHttpClientConfig.Builder builder =
        new AsyncHttpClientConfig.Builder().setUserAgent(USER_AGENT);

    int connectionTimeout = (int) CONNECTION_TIMEOUT.toMillis();
    try {
      Method setConnectTimeout =
          AsyncHttpClientConfig.Builder.class.getMethod("setConnectTimeout", int.class);
      setConnectTimeout.invoke(builder, connectionTimeout);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      builder.setRequestTimeoutInMs(connectionTimeout);
    }

    try {
      Method setFollowRedirect =
          AsyncHttpClientConfig.Builder.class.getMethod("setFollowRedirect", boolean.class);
      setFollowRedirect.invoke(builder, true);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      builder.setFollowRedirects(true);
    }

    try {
      Method setMaxRedirects =
          AsyncHttpClientConfig.Builder.class.getMethod("setMaxRedirects", int.class);
      setMaxRedirects.invoke(builder, 3);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      builder.setMaximumNumberOfRedirects(3);
    }

    try {
      Method setAllowPoolingConnections =
          AsyncHttpClientConfig.Builder.class.getMethod(
              "setAllowPoolingConnections", boolean.class);
      setAllowPoolingConnections.invoke(builder, false);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      builder.setAllowPoolingConnection(false);
    }

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
    // TODO(anuraaga): Do we also need to test ListenableFuture callback?
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

    optionsBuilder.setUserAgent(USER_AGENT);

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
          attributes.remove(SemanticAttributes.NET_PEER_NAME);
          attributes.remove(SemanticAttributes.NET_PEER_PORT);
          return attributes;
        });
  }
}
