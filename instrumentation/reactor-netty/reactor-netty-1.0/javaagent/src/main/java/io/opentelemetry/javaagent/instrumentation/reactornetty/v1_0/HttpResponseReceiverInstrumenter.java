/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorContextKeys.CLIENT_CONTEXT_KEY;
import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorContextKeys.CLIENT_PARENT_CONTEXT_KEY;
import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorNettySingletons.instrumenter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.ContextPropagationOperator;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.ConnectionObserver;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientConfig;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.http.client.HttpClientState;

public final class HttpResponseReceiverInstrumenter {

  // this method adds several stateful listeners that execute the instrumenter lifecycle during HTTP
  // request processing
  // it should be used just before one of the response*() methods is called - at this point the HTTP
  // request is no longer modifiable by the user
  @Nullable
  public static HttpClient.ResponseReceiver<?> instrument(HttpClient.ResponseReceiver<?> receiver) {
    // receiver should always be an HttpClientFinalizer, which both extends HttpClient and
    // implements ResponseReceiver
    if (receiver instanceof HttpClient) {
      HttpClient client = (HttpClient) receiver;
      HttpClientConfig config = client.configuration();

      ContextHolder contextHolder = new ContextHolder();

      HttpClient modified =
          client
              .mapConnect(new StartOperation(contextHolder, config))
              .doOnRequest(new PropagateContext(contextHolder))
              .doOnRequestError(new EndOperationWithError(contextHolder, config))
              .observe(new EndOperation(contextHolder, config));

      // modified should always be an HttpClientFinalizer too
      if (modified instanceof HttpClient.ResponseReceiver) {
        return (HttpClient.ResponseReceiver<?>) modified;
      }
    }

    return null;
  }

  static final class ContextHolder {
    volatile Context parentContext;
    volatile Context context;
  }

  static final class StartOperation
      implements Function<Mono<? extends Connection>, Mono<? extends Connection>> {

    private final ContextHolder contextHolder;
    private final HttpClientConfig config;

    StartOperation(ContextHolder contextHolder, HttpClientConfig config) {
      this.contextHolder = contextHolder;
      this.config = config;
    }

    @Override
    public Mono<? extends Connection> apply(Mono<? extends Connection> mono) {
      return Mono.defer(
          () -> {
            Context parentContext = Context.current();
            contextHolder.parentContext = parentContext;
            if (!instrumenter().shouldStart(parentContext, config)) {
              return mono.contextWrite(ctx -> ctx.put(CLIENT_PARENT_CONTEXT_KEY, parentContext));
            }

            Context context = instrumenter().start(parentContext, config);
            contextHolder.context = context;
            return ContextPropagationOperator.runWithContext(mono, context)
                // make contexts accessible via the reactor ContextView - the doOn* callbacks
                // instrumentation uses these to set the proper context
                .contextWrite(ctx -> ctx.put(CLIENT_PARENT_CONTEXT_KEY, parentContext))
                .contextWrite(ctx -> ctx.put(CLIENT_CONTEXT_KEY, context));
          });
    }
  }

  static final class PropagateContext implements BiConsumer<HttpClientRequest, Connection> {

    private final ContextHolder contextHolder;

    PropagateContext(ContextHolder contextHolder) {
      this.contextHolder = contextHolder;
    }

    @Override
    public void accept(HttpClientRequest httpClientRequest, Connection connection) {
      Context context = contextHolder.context;
      if (context != null) {
        GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .inject(context, httpClientRequest, HttpClientRequestHeadersSetter.INSTANCE);
      }

      // also propagate the context to the underlying netty instrumentation
      // if this span was suppressed and context is null, propagate parentContext - this will allow
      // netty spans to be suppressed too
      Context nettyParentContext = context == null ? contextHolder.parentContext : context;
      connection.channel().attr(AttributeKeys.WRITE_CONTEXT).set(nettyParentContext);
    }
  }

  static final class EndOperationWithError implements BiConsumer<HttpClientRequest, Throwable> {

    private final ContextHolder contextHolder;
    private final HttpClientConfig config;

    EndOperationWithError(ContextHolder contextHolder, HttpClientConfig config) {
      this.contextHolder = contextHolder;
      this.config = config;
    }

    @Override
    public void accept(HttpClientRequest httpClientRequest, Throwable error) {
      Context context = contextHolder.context;
      if (context == null) {
        return;
      }
      instrumenter().end(context, config, null, error);
    }
  }

  static final class EndOperation implements ConnectionObserver {

    private final ContextHolder contextHolder;
    private final HttpClientConfig config;

    EndOperation(ContextHolder contextHolder, HttpClientConfig config) {
      this.contextHolder = contextHolder;
      this.config = config;
    }

    @Override
    public void onStateChange(Connection connection, State newState) {
      if (newState != HttpClientState.RESPONSE_COMPLETED) {
        return;
      }

      Context context = contextHolder.context;
      if (context == null) {
        return;
      }

      // connection is actually an instance of HttpClientOperations - a package private class that
      // implements both Connection and HttpClientResponse
      if (connection instanceof HttpClientResponse) {
        HttpClientResponse response = (HttpClientResponse) connection;
        instrumenter().end(context, config, response, null);
      }
    }

    @Override
    public void onUncaughtException(Connection connection, Throwable error) {
      Context context = contextHolder.context;
      if (context == null) {
        return;
      }
      instrumenter().end(context, config, null, error);
    }
  }

  private HttpResponseReceiverInstrumenter() {}
}
