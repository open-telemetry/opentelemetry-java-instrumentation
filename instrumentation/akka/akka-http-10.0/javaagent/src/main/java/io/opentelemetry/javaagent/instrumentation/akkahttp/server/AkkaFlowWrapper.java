/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.akkahttp.server.AkkaHttpServerSingletons.errorResponse;
import static io.opentelemetry.javaagent.instrumentation.akkahttp.server.AkkaHttpServerSingletons.instrumenter;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.Attributes;
import akka.stream.BidiShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.scaladsl.Flow;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.ArrayDeque;
import java.util.Deque;

public class AkkaFlowWrapper
    extends GraphStage<BidiShape<HttpResponse, HttpResponse, HttpRequest, HttpRequest>> {
  private final Inlet<HttpRequest> requestIn = Inlet.create("otel.requestIn");
  private final Outlet<HttpRequest> requestOut = Outlet.create("otel.requestOut");
  private final Inlet<HttpResponse> responseIn = Inlet.create("otel.responseIn");
  private final Outlet<HttpResponse> responseOut = Outlet.create("otel.responseOut");

  private final BidiShape<HttpResponse, HttpResponse, HttpRequest, HttpRequest> shape =
      BidiShape.of(responseIn, responseOut, requestIn, requestOut);

  public static Flow<HttpRequest, HttpResponse, ?> wrap(
      Flow<HttpRequest, HttpResponse, ?> handler) {
    return handler.join(new AkkaFlowWrapper());
  }

  @Override
  public BidiShape<HttpResponse, HttpResponse, HttpRequest, HttpRequest> shape() {
    return shape;
  }

  @Override
  public GraphStageLogic createLogic(Attributes attributes) {
    return new TracingLogic();
  }

  private class TracingLogic extends GraphStageLogic {
    private final Deque<TracingRequest> requests = new ArrayDeque<>();

    public TracingLogic() {
      super(shape);

      // server pulls response, pass response from user code to server
      setHandler(
          responseOut,
          new AbstractOutHandler() {
            @Override
            public void onPull() {
              pull(responseIn);
            }

            @Override
            public void onDownstreamFinish() {
              cancel(responseIn);
            }
          });

      // user code pulls request, pass request from server to user code
      setHandler(
          requestOut,
          new AbstractOutHandler() {
            @Override
            public void onPull() {
              pull(requestIn);
            }

            @Override
            public void onDownstreamFinish() {
              // Invoked on errors. Don't complete this stage to allow error-capturing
              cancel(requestIn);
            }
          });

      // new request from server
      setHandler(
          requestIn,
          new AbstractInHandler() {
            @Override
            public void onPush() {
              HttpRequest request = grab(requestIn);

              TracingRequest tracingRequest = TracingRequest.EMPTY;
              Context parentContext = currentContext();
              if (instrumenter().shouldStart(parentContext, request)) {
                Context context = instrumenter().start(parentContext, request);
                // scope opened here may leak, actor instrumentation will close it
                Scope scope = context.makeCurrent();
                tracingRequest = new TracingRequest(context, scope, request);
              }
              // event if span wasn't started we need to push TracingRequest to match response
              // with request
              requests.push(tracingRequest);

              push(requestOut, request);
            }

            @Override
            public void onUpstreamFinish() {
              complete(requestOut);
            }

            @Override
            public void onUpstreamFailure(Throwable exception) {
              fail(requestOut, exception);
            }
          });

      // response from user code
      setHandler(
          responseIn,
          new AbstractInHandler() {
            @Override
            public void onPush() {
              HttpResponse response = grab(responseIn);

              TracingRequest tracingRequest = requests.poll();
              if (tracingRequest != null && tracingRequest != TracingRequest.EMPTY) {
                // this may happen on a different thread from the one that opened the scope
                // actor instrumentation will take care of the leaked scopes
                tracingRequest.scope.close();
                instrumenter().end(tracingRequest.context, tracingRequest.request, response, null);
              }
              push(responseOut, response);
            }

            @Override
            public void onUpstreamFailure(Throwable exception) {
              TracingRequest tracingRequest;
              while ((tracingRequest = requests.poll()) != null) {
                if (tracingRequest == TracingRequest.EMPTY) {
                  continue;
                }
                tracingRequest.scope.close();
                instrumenter()
                    .end(
                        tracingRequest.context, tracingRequest.request, errorResponse(), exception);
              }

              fail(responseOut, exception);
            }

            @Override
            public void onUpstreamFinish() {
              completeStage();
            }
          });
    }
  }

  private static class TracingRequest {
    static final TracingRequest EMPTY = new TracingRequest(null, null, null);
    Context context;
    Scope scope;
    HttpRequest request;

    TracingRequest(Context context, Scope scope, HttpRequest request) {
      this.context = context;
      this.scope = scope;
      this.request = request;
    }
  }
}
