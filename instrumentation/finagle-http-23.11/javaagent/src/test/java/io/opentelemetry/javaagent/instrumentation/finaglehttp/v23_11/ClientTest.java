/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11.Utils.createClient;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import com.twitter.finagle.ConnectionFailedException;
import com.twitter.finagle.Failure;
import com.twitter.finagle.ReadTimedOutException;
import com.twitter.finagle.Service;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.util.Await;
import com.twitter.util.Duration;
import com.twitter.util.Future;
import com.twitter.util.FuturePool;
import com.twitter.util.Time;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.timeout.ReadTimeoutException;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11.Utils.ClientType;
import java.net.ConnectException;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests relevant client functionality.
 *
 * @implNote Why no http/2 tests: finagle maps everything down to http/1.1 via netty's own {@link
 *     Http2StreamFrameToHttpObjectCodec} which results in the same code path execution through
 *     finagle's netty stack. While testing would undoubtedly be beneficial, it's at this time
 *     untested due to lack of concrete support from the otel instrumentation test framework and
 *     upstream netty instrumentation, both.
 */
// todo implement http/2-specific tests;
//  otel test framework doesn't support an http/2 server out of the box
class ClientTest extends AbstractHttpClientTest<Request> {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private final Map<ClientType, Service<Request, Response>> clients = new ConcurrentHashMap<>();

  // finagle Services are closeable, but are bound to a host + port;
  // as these are only known during the invocation of the test, each test must create and then
  // tear down their respective Services.
  //
  // however, the underlying netty bits are reused between Services by default, so "close"
  // works out to a more "virtual" operation than with other client libraries.
  @AfterEach
  void tearDown() throws Exception {
    for (Service<Request, Response> client : clients.values()) {
      Await.ready(client.close(Time.fromSeconds(10)));
    }
    clients.clear();
  }

  private Service<Request, Response> getClient(URI uri) {
    return getClient(uri, uri.getScheme().equals("https") ? ClientType.TLS : ClientType.DEFAULT);
  }

  private Service<Request, Response> getClient(URI uri, ClientType clientType) {
    return clients.computeIfAbsent(
        clientType,
        (type) -> createClient(type).newService(uri.getHost() + ":" + Utils.safePort(uri)));
  }

  private Future<Response> doSendRequest(Request request, URI uri) {
    // push this onto a FuturePool for 2 reasons:
    //  1) forces the request handling onto a different thread, ensuring test accuracy
    //  2) using the default thread can mess with high concurrency scenarios
    Context context = Context.current();
    return FuturePool.unboundedPool()
        .apply(
            () -> {
              try (Scope ignored = context.makeCurrent()) {
                return Await.result(getClient(uri).apply(request));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.setSingleConnectionFactory(
        (host, port) -> {
          URI uri = URI.create(String.format(Locale.ROOT, "http://%s:%d", host, port));
          Service<Request, Response> svc = getClient(uri, ClientType.SINGLE_CONN);
          return (path, headers) -> {
            // this is synchronized bc so is the Netty one;
            // seems like the use of a "single" (presumably-queueing) connection would do this
            // automatically, but apparently not
            synchronized (svc) {
              Request get = buildRequest("GET", URI.create(uri + path), headers);
              return Await.result(svc.apply(get), Duration.fromSeconds(20)).statusCode();
            }
          };
        });

    optionsBuilder.setHttpAttributes(ClientTest::getHttpAttributes);
    optionsBuilder.setExpectedClientSpanNameMapper(ClientTest::getExpectedClientSpanName);
    optionsBuilder.disableTestRedirects();
    optionsBuilder.spanEndsAfterBody();
    optionsBuilder.setClientSpanErrorMapper(
        (uri, error) -> {
          // all errors should be wrapped in RuntimeExceptions due to how we run things in
          // doSendRequest()
          AbstractThrowableAssert<?, ?> clientWrapAssert =
              assertThat(error).isInstanceOf(RuntimeException.class);
          if ("http://localhost:61/".equals(uri.toString())
              || "https://192.0.2.1/".equals(uri.toString())) {
            // finagle handles all these in com.twitter.finagle.netty4.ConnectionBuilder.build();
            // all errors emitted by the netty Bootstrap.connect() call are mapped to
            // twitter/finagle exceptions and handled accordingly;
            // namely, this means wrapping the root exception in a finagle
            // ConnectionFailedException
            // and then with a twitter Failure.rejected() call, resulting in the multiple nestings
            // of the root exception
            clientWrapAssert
                .cause()
                .isInstanceOf(Failure.class)
                .cause()
                .isInstanceOf(ConnectionFailedException.class)
                .cause()
                .isInstanceOf(ConnectException.class);
            error = error.getCause().getCause().getCause();
          } else if (uri.getPath().endsWith("/read-timeout")) {
            // not a connect() exception like the above, so is not wrapped as above;
            clientWrapAssert.cause().isInstanceOf(ReadTimedOutException.class);
            // however, this specific case results in a mapping from netty's ReadTimeoutException
            // to finagle's ReadTimedOutException in the finagle client code, losing all trace of
            // the original exception; so we must construct it manually here
            error = new ReadTimeoutException();
          }
          return error;
        });
  }

  @Override
  public Request buildRequest(String method, URI uri, Map<String, String> headers) {
    return Utils.buildRequest(method, uri, headers);
  }

  @Override
  public int sendRequest(Request request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    return Await.result(doSendRequest(request, uri), Duration.fromSeconds(30)).statusCode();
  }

  @Override
  public void sendRequestWithCallback(
      Request request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    doSendRequest(request, uri)
        .onSuccess(
            r -> {
              httpClientResult.complete(r.statusCode());
              return null;
            })
        .onFailure(
            t -> {
              httpClientResult.complete(t);
              return null;
            });
  }

  private static Set<AttributeKey<?>> getHttpAttributes(URI uri) {
    String uriString = uri.toString();
    // http://localhost:61/ => unopened port, https://192.0.2.1/ => non routable address
    if ("http://localhost:61/".equals(uriString) || "https://192.0.2.1/".equals(uriString)) {
      return Collections.emptySet();
    }
    Set<AttributeKey<?>> attributes = new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
    attributes.remove(SERVER_ADDRESS);
    attributes.remove(SERVER_PORT);
    return attributes;
  }

  // borrowed from AbstractNetty41ClientTest as finagle's underlying framework under test here is
  // netty
  private static String getExpectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "https://192.0.2.1/": // non routable address
        return "CONNECT";
      default:
        return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(uri, method);
    }
  }
}
