/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.QueryParams;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractArmeriaHttpServerTest extends AbstractHttpServerTest<Server> {

  protected abstract ServerBuilder configureServer(ServerBuilder serverBuilder);

  @Override
  protected Server setupServer() {
    ServerBuilder sb = Server.builder();

    sb.http(port);

    sb.service(
        SUCCESS.getPath(),
        (ctx, req) ->
            testing()
                .runWithSpan(
                    "controller",
                    () ->
                        HttpResponse.of(
                            HttpStatus.valueOf(SUCCESS.getStatus()),
                            MediaType.PLAIN_TEXT_UTF_8,
                            SUCCESS.getBody())));

    sb.service(
        REDIRECT.getPath(),
        (ctx, req) ->
            testing()
                .runWithSpan(
                    "controller",
                    () ->
                        HttpResponse.of(
                            ResponseHeaders.of(
                                HttpStatus.valueOf(REDIRECT.getStatus()),
                                HttpHeaderNames.LOCATION,
                                REDIRECT.getBody()))));

    sb.service(
        ERROR.getPath(),
        (ctx, req) ->
            testing()
                .runWithSpan(
                    "controller",
                    () ->
                        HttpResponse.of(
                            HttpStatus.valueOf(ERROR.getStatus()),
                            MediaType.PLAIN_TEXT_UTF_8,
                            ERROR.getBody())));

    sb.service(
        EXCEPTION.getPath(),
        (ctx, req) ->
            testing()
                .runWithSpan(
                    "controller",
                    () -> {
                      throw new Exception(EXCEPTION.getBody());
                    }));

    sb.service(
        "/query",
        (ctx, req) ->
            testing()
                .runWithSpan(
                    "controller",
                    () ->
                        HttpResponse.of(
                            HttpStatus.valueOf(QUERY_PARAM.getStatus()),
                            MediaType.PLAIN_TEXT_UTF_8,
                            "some=" + QueryParams.fromQueryString(ctx.query()).get("some"))));

    sb.service(
        "/path/:id/param",
        (ctx, req) ->
            testing()
                .runWithSpan(
                    "controller",
                    () ->
                        HttpResponse.of(
                            HttpStatus.valueOf(PATH_PARAM.getStatus()),
                            MediaType.PLAIN_TEXT_UTF_8,
                            ctx.pathParam("id"))));

    sb.service(
        "/child",
        (ctx, req) ->
            testing()
                .runWithSpan(
                    "controller",
                    () -> {
                      INDEXED_CHILD.collectSpanAttributes(
                          name -> QueryParams.fromQueryString(ctx.query()).get(name));
                      return HttpResponse.of(
                          HttpStatus.valueOf(INDEXED_CHILD.getStatus()),
                          MediaType.PLAIN_TEXT_UTF_8,
                          INDEXED_CHILD.getBody());
                    }));

    sb.service(
        "/captureHeaders",
        (ctx, req) ->
            testing()
                .runWithSpan(
                    "controller",
                    () ->
                        HttpResponse.of(
                            ResponseHeaders.of(
                                HttpStatus.valueOf(CAPTURE_HEADERS.getStatus()),
                                "X-Test-Response",
                                req.headers().get("X-Test-Request"),
                                HttpHeaderNames.CONTENT_TYPE,
                                MediaType.PLAIN_TEXT_UTF_8),
                            HttpData.ofUtf8(CAPTURE_HEADERS.getBody()))));

    // Make sure user decorators see spans.
    sb.decorator(
        (delegate, ctx, req) -> {
          if (!Span.current().getSpanContext().isValid()) {
            // Return an invalid code to fail any assertion
            return HttpResponse.of(600);
          }
          ctx.addAdditionalResponseHeader("decoratinghttpservicefunction", "ok");
          return delegate.serve(ctx, req);
        });

    sb.decorator(
        delegate ->
            (ctx, req) -> {
              if (!Span.current().getSpanContext().isValid()) {
                // Return an invalid code to fail any assertion
                return HttpResponse.of(601);
              }
              ctx.addAdditionalResponseHeader("decoratingfunction", "ok");
              return delegate.serve(ctx, req);
            });

    configureServer(sb);

    Server server = sb.build();
    server.start().join();

    return server;
  }

  @Override
  protected void stopServer(Server server) {
    server.stop();
  }

  @Override
  protected final void configure(HttpServerTestOptions options) {
    options.setExpectedHttpRoute(
        endpoint -> {
          if (endpoint == ServerEndpoint.NOT_FOUND) {
            // TODO(anuraaga): Revisit this when applying instrumenters to more libraries, Armeria
            // currently reports '/*' which is a fallback route.
            return "/*";
          }
          return expectedHttpRoute(endpoint);
        });

    options.setHttpAttributes(
        endpoint -> {
          Set<AttributeKey<?>> keys = new HashSet<>(HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          keys.add(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH);
          keys.add(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH);
          keys.add(SemanticAttributes.HTTP_SERVER_NAME);
          return keys;
        });

    options.setTestPathParam(true);
  }
}
