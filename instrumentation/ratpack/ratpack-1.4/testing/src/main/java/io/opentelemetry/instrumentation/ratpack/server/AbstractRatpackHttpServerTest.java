/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import ratpack.error.ServerErrorHandler;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import ratpack.server.RatpackServer;
import ratpack.server.RatpackServerSpec;

public abstract class AbstractRatpackHttpServerTest extends AbstractHttpServerTest<RatpackServer> {

  protected static final ServerEndpoint POST_STREAM =
      new ServerEndpoint(
          "POST_STREAM", "post-stream", SUCCESS.getStatus(), SUCCESS.getBody(), false);

  @SuppressWarnings("CheckedExceptionNotThrown")
  @Override
  protected RatpackServer setupServer() throws Exception {
    return RatpackServer.start(
        ratpackServerSpec -> {
          ratpackServerSpec.serverConfig(
              serverConfigBuilder -> {
                serverConfigBuilder.port(port);
                serverConfigBuilder.address(InetAddress.getByName("localhost"));
              });
          ratpackServerSpec.handlers(
              handlerChain -> {
                handlerChain.register(
                    registrySpec ->
                        registrySpec.add(ServerErrorHandler.class, new TestErrorHandler()));
                handlerChain.prefix(
                    SUCCESS.rawPath(),
                    chain ->
                        chain.all(
                            context ->
                                process(
                                    SUCCESS,
                                    endpoint ->
                                        controller(
                                            endpoint,
                                            () ->
                                                context
                                                    .getResponse()
                                                    .status(endpoint.getStatus())
                                                    .send(endpoint.getBody())))));

                handlerChain.prefix(
                    INDEXED_CHILD.rawPath(),
                    chain ->
                        chain.all(
                            context ->
                                process(
                                    INDEXED_CHILD,
                                    endpoint ->
                                        controller(
                                            endpoint,
                                            () -> {
                                              endpoint.collectSpanAttributes(
                                                  name ->
                                                      context
                                                          .getRequest()
                                                          .getQueryParams()
                                                          .get(name));
                                              context
                                                  .getResponse()
                                                  .status(endpoint.getStatus())
                                                  .send();
                                            }))));

                handlerChain.prefix(
                    QUERY_PARAM.rawPath(),
                    chain ->
                        chain.all(
                            context ->
                                process(
                                    QUERY_PARAM,
                                    endpoint ->
                                        controller(
                                            endpoint,
                                            () ->
                                                context
                                                    .getResponse()
                                                    .status(endpoint.getStatus())
                                                    .send(context.getRequest().getQuery())))));

                handlerChain.prefix(
                    REDIRECT.rawPath(),
                    chain ->
                        chain.all(
                            context ->
                                process(
                                    REDIRECT,
                                    endpoint ->
                                        controller(
                                            endpoint,
                                            () -> context.redirect(endpoint.getBody())))));

                handlerChain.prefix(
                    ERROR.rawPath(),
                    chain ->
                        chain.all(
                            context ->
                                process(
                                    ERROR,
                                    endpoint ->
                                        controller(
                                            endpoint,
                                            () ->
                                                context
                                                    .getResponse()
                                                    .status(endpoint.getStatus())
                                                    .send(endpoint.getBody())))));

                handlerChain.prefix(
                    EXCEPTION.rawPath(),
                    chain ->
                        chain.all(
                            context ->
                                process(
                                    EXCEPTION,
                                    endpoint ->
                                        controller(
                                            endpoint,
                                            () -> {
                                              throw new IllegalStateException(endpoint.getBody());
                                            }))));

                handlerChain.prefix(
                    "path/:id/param",
                    chain ->
                        chain.all(
                            context ->
                                process(
                                    PATH_PARAM,
                                    endpoint ->
                                        controller(
                                            endpoint,
                                            () ->
                                                context
                                                    .getResponse()
                                                    .status(endpoint.getStatus())
                                                    .send(context.getPathTokens().get("id"))))));

                handlerChain.prefix(
                    CAPTURE_HEADERS.rawPath(),
                    chain ->
                        chain.all(
                            context ->
                                process(
                                    CAPTURE_HEADERS,
                                    endpoint ->
                                        controller(
                                            endpoint,
                                            () -> {
                                              context.getResponse().status(endpoint.getStatus());
                                              context
                                                  .getResponse()
                                                  .getHeaders()
                                                  .set(
                                                      "X-Test-Response",
                                                      context
                                                          .getRequest()
                                                          .getHeaders()
                                                          .get("X-Test-Request"));
                                              context.getResponse().send(endpoint.getBody());
                                            }))));

                handlerChain.prefix(
                    POST_STREAM.rawPath(),
                    chain ->
                        chain.all(
                            context ->
                                process(POST_STREAM, endpoint -> handlePostStream(context))));

                registerHandlers(handlerChain);
              });

          configure(ratpackServerSpec);
        });
  }

  protected void registerHandlers(Chain chain) throws Exception {}

  protected void process(ServerEndpoint endpoint, Consumer<ServerEndpoint> consumer) {
    consumer.accept(endpoint);
  }

  private void handlePostStream(Context context) {
    controller(
        POST_STREAM,
        () -> {
          context
              .getRequest()
              .getBodyStream()
              .subscribe(
                  new Subscriber<ByteBuf>() {
                    private Subscription subscription;
                    private int count;
                    private String traceId;

                    @Override
                    public void onSubscribe(Subscription subscription) {
                      this.subscription = subscription;
                      traceId = Span.current().getSpanContext().getTraceId();
                      subscription.request(1);
                    }

                    @Override
                    public void onNext(ByteBuf byteBuf) {
                      assertThat(Span.current().getSpanContext().getTraceId()).isEqualTo(traceId);
                      if (count < 2) {
                        testing().runWithSpan("onNext", () -> count++);
                      }
                      byteBuf.release();
                      subscription.request(1);
                    }

                    @SuppressWarnings("SystemOut")
                    @Override
                    public void onError(Throwable throwable) {
                      // prints the assertion error from onNext
                      throwable.printStackTrace();
                      context.getResponse().status(500).send(throwable.getMessage());
                    }

                    @Override
                    public void onComplete() {
                      testing()
                          .runWithSpan(
                              "onComplete",
                              () -> context.getResponse().status(200).send(POST_STREAM.getBody()));
                    }
                  });
        });
  }

  // TODO: The default Ratpack error handler also returns a 500 which is all we test, so
  // we don't actually have test coverage ensuring our instrumentation correctly delegates to this
  // user registered handler.
  private static class TestErrorHandler implements ServerErrorHandler {
    @Override
    public void error(Context context, Throwable throwable) {
      context.getResponse().status(500).send(throwable.getMessage());
    }
  }

  @Override
  protected void stopServer(RatpackServer server) throws Exception {
    server.stop();
  }

  protected abstract void configure(RatpackServerSpec serverSpec) throws Exception;

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);

    options.setHasHandlerSpan(endpoint -> true);
    options.setTestPathParam(true);
    // server spans are ended inside the controller spans
    options.setVerifyServerSpanEndTime(false);

    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (endpoint.getStatus() == 404) {
            return "/";
          } else if (endpoint == PATH_PARAM) {
            return "/path/:id/param";
          } else {
            return endpoint.getPath();
          }
        });
  }

  @Override
  protected SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    String spanName;
    if (endpoint.getStatus() == 404) {
      spanName = "/";
    } else if (endpoint == PATH_PARAM) {
      spanName = "/path/:id/param";
    } else {
      spanName = endpoint.getPath();
    }
    span.hasName(spanName).hasKind(SpanKind.INTERNAL);
    if (endpoint == EXCEPTION) {
      span.hasStatus(StatusData.error())
          .hasException(new IllegalStateException(EXCEPTION.getBody()));
    }
    return span;
  }

  protected boolean testPostStream() {
    return true;
  }

  @Test
  void postStream() {
    Assumptions.assumeTrue(testPostStream());

    // body should be large enough to trigger multiple calls to onNext
    String body = String.join("", Collections.nCopies(10000, "foobar"));
    AggregatedHttpResponse response =
        client.post(resolveAddress(POST_STREAM), body).aggregate().join();

    assertThat(response.status().code()).isEqualTo(POST_STREAM.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(POST_STREAM.getBody());

    boolean hasHandlerSpan = hasHandlerSpan(POST_STREAM);
    // when using javaagent instrumentation the parent of reactive callbacks is the controller span
    // where subscribe was called, for library instrumentation server span is the parent
    int reactiveCallbackParent = hasHandlerSpan ? 2 : 0;

    testing()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
              assertions.add(
                  span -> span.hasName("POST /post-stream").hasKind(SpanKind.SERVER).hasNoParent());
              if (hasHandlerSpan) {
                assertions.add(
                    span ->
                        span.hasName("/post-stream")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)));
              }
              assertions.add(
                  span ->
                      span.hasName("controller")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(hasHandlerSpan ? 1 : 0)));
              assertions.add(
                  span ->
                      span.hasName("onNext")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(reactiveCallbackParent)));
              assertions.add(
                  span ->
                      span.hasName("onNext")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(reactiveCallbackParent)));
              assertions.add(
                  span ->
                      span.hasName("onComplete")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(reactiveCallbackParent)));
              trace.hasSpansSatisfyingExactly(assertions);
            });
  }
}
