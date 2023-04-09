/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finagle;

import static io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import com.google.common.collect.Sets;
import com.twitter.finagle.ListeningServer;
import com.twitter.finagle.Service;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.finagle.http.Status;
import com.twitter.io.Buf;
import com.twitter.util.Await;
import com.twitter.util.Duration;
import com.twitter.util.Future;
import com.twitter.util.logging.Logging;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.URI;
import java.util.Collections;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractServerTest extends AbstractHttpServerTest<ListeningServer> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setTestException(false);
    options.setHttpAttributes(
        unused ->
            Sets.difference(
                DEFAULT_HTTP_ATTRIBUTES, Collections.singleton(SemanticAttributes.HTTP_ROUTE)));

    options.setTestCaptureHttpHeaders(true);
  }

  @Override
  protected void stopServer(ListeningServer server) throws Exception {
    Await.ready(server.close(), Duration.fromSeconds(2));
  }

  static class TestService extends Service<Request, Response> implements Logging {
    @Override
    public Future<Response> apply(Request request) {
      URI uri = URI.create(request.uri());
      ServerEndpoint endpoint = ServerEndpoint.forPath(uri.getPath());
      return controller(
          endpoint,
          () -> {
            Response response = Response.apply().status(Status.apply(endpoint.getStatus()));
            if (SUCCESS.equals(endpoint) || ERROR.equals(endpoint)) {
              response.content(Buf.Utf8$.MODULE$.apply(endpoint.getBody()));
            } else if (INDEXED_CHILD.equals(endpoint)) {
              endpoint.collectSpanAttributes(
                  name ->
                      new QueryStringDecoder(uri)
                          .parameters().get(name).stream().findFirst().orElse(""));
              response.content(Buf.Empty());
            } else if (QUERY_PARAM.equals(endpoint)) {
              response.content(Buf.Utf8$.MODULE$.apply(uri.getQuery()));
            } else if (REDIRECT.equals(endpoint)) {
              response.content(Buf.Empty());
              response.headerMap().put(HttpHeaderNames.LOCATION.toString(), endpoint.getBody());
            } else if (CAPTURE_HEADERS.equals(endpoint)) {
              response.content(Buf.Utf8$.MODULE$.apply(endpoint.getBody()));
              response
                  .headerMap()
                  .set("X-Test-Response", request.headerMap().get("X-Test-Request").get());
            } else if (EXCEPTION.equals(endpoint)) {
              throw new IllegalStateException(endpoint.getBody());
            } else {
              response.content(Buf.Utf8$.MODULE$.apply(NOT_FOUND.getBody()));
              response = Response.apply().status(Status.apply(NOT_FOUND.getStatus()));
            }
            return Future.value(response);
          });
    }
  }
}
