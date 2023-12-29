/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finagle;

import com.twitter.finagle.Service;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.util.Await;
import com.twitter.util.Duration;
import com.twitter.util.Future;
import com.twitter.util.FuturePool;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.ConnectException;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
public class ClientTest extends AbstractHttpClientTest<Request> {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  // todo implement http/2-specific tests;
  //  otel test framework doesn't support an http/2 server out of the box
  @RegisterExtension
  static final FinagleClientExtension extension = FinagleClientExtension.http_1();

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

  private static Set<AttributeKey<?>> getHttpAttributes(URI uri) {
    String uriString = uri.toString();
    // http://localhost:61/ => unopened port, https://192.0.2.1/ => non routable address
    if ("http://localhost:61/".equals(uriString) || "https://192.0.2.1/".equals(uriString)) {
      return Collections.emptySet();
    }
    Set<AttributeKey<?>> attributes = new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
    attributes.remove(SemanticAttributes.SERVER_ADDRESS);
    attributes.remove(SemanticAttributes.SERVER_PORT);
    return attributes;
  }

  @Override
  public Request buildRequest(String method, URI uri, Map<String, String> headers) {
    return FinagleClientExtension.buildRequest(method, uri, headers);
  }

  @Override
  public int sendRequest(Request request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    return Await.result(doSendRequest(request, uri), Duration.fromSeconds(10)).statusCode();
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

  private static Future<Response> doSendRequest(Request request, URI uri) {
    return FuturePool.unboundedPool()
        .apply(
            () -> {
              try {
                return Await.result(extension.clientFor(uri).apply(request));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.setSingleConnectionFactory(
        (host, port) -> {
          URI uri = URI.create(String.format("http://%s:%d", host, port));
          Service<Request, Response> svc =
              extension.builderFor(uri).withSessionPool().maxSize(1).newService(host + ":" + port);
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
    optionsBuilder.setClientSpanErrorMapper(
        (uri, error) -> {
          if ("http://localhost:61/".equals(uri.toString())) {
            // slightly modified version of what one might see with netty 4.1;
            // finagle injects its own prefix
            error =
                new ConnectException(
                    "finishConnect(..) failed: Connection refused: localhost/127.0.0.1:61");
          } else if (uri.getPath().endsWith("/read-timeout")) {
            // see the netty error exception extractor -- this is lifted from an Annotated*
            // counterpart with a private constructor in netty
            error = new io.netty.handler.timeout.ReadTimeoutException();
          } else if ("https://192.0.2.1/".equals(uri.toString())) {
            error = new ConnectTimeoutException();
          }
          return error;
        });
  }
}
