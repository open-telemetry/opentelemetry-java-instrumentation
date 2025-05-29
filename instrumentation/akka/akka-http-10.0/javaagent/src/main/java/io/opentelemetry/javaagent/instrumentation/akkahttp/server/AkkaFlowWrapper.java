/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.akkahttp.server.AkkaHttpServerSingletons.errorResponse;
import static io.opentelemetry.javaagent.instrumentation.akkahttp.server.AkkaHttpServerSingletons.instrumenter;

import akka.http.javadsl.model.HttpHeader;
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
import akka.stream.stage.OutHandler;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.instrumentation.akkahttp.server.route.AkkaRouteHolder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

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

  public static Context getContext(OutHandler outHandler) {
    if (outHandler instanceof TracingLogic.ApplicationOutHandler) {
      // We have multiple requests here only when requests are pipelined on the same connection.
      // It appears that these requests are processed one by one so processing next request won't
      // be started before the first one has returned a response, because of this the first request
      // in the queue is always the one that is currently being processed.
      TracingRequest request =
          ((TracingLogic.ApplicationOutHandler) outHandler).getRequests().peek();
      if (request != null) {
        return request.context;
      }
    }

    return null;
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
          new ApplicationOutHandler() {
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
                context = AkkaRouteHolder.init(context);
                tracingRequest = new TracingRequest(context, request);
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
                // akka response is immutable so the customizer just captures the added headers
                AkkaHttpResponseMutator responseMutator = new AkkaHttpResponseMutator();
                HttpServerResponseCustomizerHolder.getCustomizer()
                    .customize(tracingRequest.context, response, responseMutator);
                // build a new response with the added headers
                List<HttpHeader> headers = responseMutator.getHeaders();
                if (!headers.isEmpty()) {
                  response = (HttpResponse) response.addHeaders(headers);
                }

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

    abstract class ApplicationOutHandler extends AbstractOutHandler {
      Deque<TracingRequest> getRequests() {
        return requests;
      }
    }
  }

  private static class TracingRequest {
    static final TracingRequest EMPTY = new TracingRequest(null, null);
    final Context context;
    final HttpRequest request;

    TracingRequest(Context context, HttpRequest request) {
      this.context = context;
      this.request = request;
    }
  }
}
