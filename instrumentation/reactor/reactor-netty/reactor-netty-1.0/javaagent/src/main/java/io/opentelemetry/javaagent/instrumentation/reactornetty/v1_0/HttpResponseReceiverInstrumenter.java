/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorContextKeys.CONTEXTS_HOLDER_KEY;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientResend;
import io.opentelemetry.instrumentation.netty.v4_1.NettyClientTelemetry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

public final class HttpResponseReceiverInstrumenter {

  // this method adds several stateful listeners that execute the instrumenter lifecycle during HTTP
  // request processing
  // it should be used just before one of the response*() methods is called - after this point the
  // HTTP request is no longer modifiable by the user
  @Nullable
  public static HttpClient.ResponseReceiver<?> instrument(HttpClient.ResponseReceiver<?> receiver) {
    // receiver should always be an HttpClientFinalizer, which both extends HttpClient and
    // implements ResponseReceiver
    if (receiver instanceof HttpClient) {
      HttpClient client = (HttpClient) receiver;

      InstrumentationContexts instrumentationContexts = new InstrumentationContexts();

      HttpClient modified =
          client
              .mapConnect(new CaptureParentContext(instrumentationContexts))
              .doOnRequestError(new EndOperationWithRequestError(instrumentationContexts))
              .doOnRequest(new StartOperation(instrumentationContexts))
              .doOnResponseError(new EndOperationWithResponseError(instrumentationContexts))
              .doAfterResponseSuccess(new EndOperationWithSuccess(instrumentationContexts))
              // end the current span on redirects; StartOperation will start another one for the
              // next resend
              .doOnRedirect(new EndOperationWithSuccess(instrumentationContexts));

      // modified should always be an HttpClientFinalizer too
      if (modified instanceof HttpClient.ResponseReceiver) {
        return (HttpClient.ResponseReceiver<?>) modified;
      }
    }

    return null;
  }

  private static final class CaptureParentContext
      implements Function<Mono<? extends Connection>, Mono<? extends Connection>> {

    private final InstrumentationContexts instrumentationContexts;

    CaptureParentContext(InstrumentationContexts instrumentationContexts) {
      this.instrumentationContexts = instrumentationContexts;
    }

    @Override
    public Mono<? extends Connection> apply(Mono<? extends Connection> mono) {
      return Mono.defer(
              () -> {
                Context parentContext = Context.current();
                instrumentationContexts.initialize(parentContext);
                // make contexts accessible via the reactor ContextView - the doOn* callbacks
                // instrumentation uses this to set the proper context for callbacks
                return mono.contextWrite(
                    ctx -> ctx.put(CONTEXTS_HOLDER_KEY, instrumentationContexts));
              })
          // if there's still any span in flight, end it
          .doOnCancel(() -> instrumentationContexts.endClientSpan(null, null));
    }
  }

  private static final class StartOperation implements BiConsumer<HttpClientRequest, Connection> {

    private final InstrumentationContexts instrumentationContexts;

    StartOperation(InstrumentationContexts instrumentationContexts) {
      this.instrumentationContexts = instrumentationContexts;
    }

    @Override
    public void accept(HttpClientRequest request, Connection connection) {
      Context context = instrumentationContexts.startClientSpan(request);

      // also propagate the context to the underlying netty instrumentation
      // if this span was suppressed and context is null, propagate parentContext - this will allow
      // netty spans to be suppressed too
      Context nettyParentContext =
          context == null ? instrumentationContexts.getParentContext() : context;
      NettyClientTelemetry.setChannelContext(connection.channel(), nettyParentContext);
    }
  }

  private static final class EndOperationWithRequestError
      implements BiConsumer<HttpClientRequest, Throwable> {

    private final InstrumentationContexts instrumentationContexts;

    EndOperationWithRequestError(InstrumentationContexts instrumentationContexts) {
      this.instrumentationContexts = instrumentationContexts;
    }

    @Override
    public void accept(HttpClientRequest request, Throwable error) {
      instrumentationContexts.endClientSpan(null, error);

      if (HttpClientResend.get(instrumentationContexts.getParentContext()) == 0) {
        // TODO: emit connection error span

        // FIXME: this branch requires lots of changes around the NettyConnectionInstrumenter
        // currently it also creates that connection error span (when the connection telemetry is
        // turned off), but without HTTP semantics - it does not have access to any HTTP information
        // after all
        // it should be possible to completely disable it, and just start and end the span here
        // this requires lots of refactoring and pretty uninteresting changes in the netty code, so
        // I'll do that in a separate PR - for better readability
      }
    }
  }

  private static final class EndOperationWithResponseError
      implements BiConsumer<HttpClientResponse, Throwable> {

    private final InstrumentationContexts instrumentationContexts;

    EndOperationWithResponseError(InstrumentationContexts instrumentationContexts) {
      this.instrumentationContexts = instrumentationContexts;
    }

    @Override
    public void accept(HttpClientResponse response, Throwable error) {
      instrumentationContexts.endClientSpan(response, error);
    }
  }

  private static final class EndOperationWithSuccess
      implements BiConsumer<HttpClientResponse, Connection> {

    private final InstrumentationContexts instrumentationContexts;

    EndOperationWithSuccess(InstrumentationContexts instrumentationContexts) {
      this.instrumentationContexts = instrumentationContexts;
    }

    @Override
    public void accept(HttpClientResponse response, Connection connection) {
      instrumentationContexts.endClientSpan(response, null);
    }
  }

  private HttpResponseReceiverInstrumenter() {}
}
