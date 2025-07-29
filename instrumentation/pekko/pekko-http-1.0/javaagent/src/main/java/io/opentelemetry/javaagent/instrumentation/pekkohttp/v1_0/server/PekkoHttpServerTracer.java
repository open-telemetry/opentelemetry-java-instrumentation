/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.PekkoHttpServerSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route.PekkoRouteHolder;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import org.apache.pekko.stream.Attributes;
import org.apache.pekko.stream.BidiShape;
import org.apache.pekko.stream.Inlet;
import org.apache.pekko.stream.Outlet;
import org.apache.pekko.stream.scaladsl.BidiFlow;
import org.apache.pekko.stream.stage.AbstractInHandler;
import org.apache.pekko.stream.stage.AbstractOutHandler;
import org.apache.pekko.stream.stage.GraphStage;
import org.apache.pekko.stream.stage.GraphStageLogic;

public class PekkoHttpServerTracer
    extends GraphStage<BidiShape<HttpResponse, HttpResponse, HttpRequest, HttpRequest>> {
  private final Inlet<HttpRequest> requestIn = Inlet.create("otel.requestIn");
  private final Outlet<HttpRequest> requestOut = Outlet.create("otel.requestOut");
  private final Inlet<HttpResponse> responseIn = Inlet.create("otel.responseIn");
  private final Outlet<HttpResponse> responseOut = Outlet.create("otel.responseOut");

  private final BidiShape<HttpResponse, HttpResponse, HttpRequest, HttpRequest> shape =
      BidiShape.of(responseIn, responseOut, requestIn, requestOut);

  public static BidiFlow<HttpResponse, ?, ?, HttpRequest, ?> wrap(
      BidiFlow<HttpResponse, ?, ?, HttpRequest, ?> handler) {
    return BidiFlow.fromGraph(new PekkoHttpServerTracer()).atopMat(handler, (a, b) -> b);
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
    private final Queue<PekkoTracingRequest> requests = new ArrayDeque<>();

    TracingLogic() {
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
            public void onDownstreamFinish(Throwable cause) {
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
            public void onDownstreamFinish(Throwable cause) {
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
              PekkoTracingRequest tracingRequest = PekkoTracingRequest.EMPTY;
              Context parentContext = currentContext();
              if (instrumenter().shouldStart(parentContext, request)) {
                Context context = instrumenter().start(parentContext, request);
                context = PekkoRouteHolder.init(context);
                tracingRequest = new PekkoTracingRequest(context, request);
                request =
                    (HttpRequest)
                        request.addAttribute(PekkoTracingRequest.ATTR_KEY, tracingRequest);
              }
              // event if span wasn't started we need to push TracingRequest to match response
              // with request
              requests.add(tracingRequest);

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

              PekkoTracingRequest tracingRequest = requests.poll();
              if (tracingRequest != null && tracingRequest != PekkoTracingRequest.EMPTY) {
                // pekko response is immutable so the customizer just captures the added headers
                PekkoHttpResponseMutator responseMutator = new PekkoHttpResponseMutator();
                HttpServerResponseCustomizerHolder.getCustomizer()
                    .customize(tracingRequest.context, response, responseMutator);
                // build a new response with the added headers
                List<HttpHeader> headers = responseMutator.getHeaders();
                if (!headers.isEmpty()) {
                  response = (HttpResponse) response.addHeaders(headers);
                }
                PekkoRouteHolder routeHolder = PekkoRouteHolder.get(tracingRequest.context);
                if (routeHolder != null) {
                  routeHolder.pushIfNotCompletelyMatched("*");
                  HttpServerRoute.update(
                      tracingRequest.context,
                      HttpServerRouteSource.CONTROLLER,
                      routeHolder.route());
                }

                instrumenter().end(tracingRequest.context, tracingRequest.request, response, null);
              }
              push(responseOut, response);
            }

            @Override
            public void onUpstreamFailure(Throwable exception) {
              // End the span for the request that failed
              PekkoTracingRequest tracingRequest = requests.poll();
              if (tracingRequest != null && tracingRequest != PekkoTracingRequest.EMPTY) {
                instrumenter()
                    .end(
                        tracingRequest.context,
                        tracingRequest.request,
                        PekkoHttpServerSingletons.errorResponse(),
                        exception);
              }

              fail(responseOut, exception);
            }

            @Override
            public void onUpstreamFinish() {
              // End any ongoing spans, though there should be none.
              PekkoTracingRequest tracingRequest;
              while ((tracingRequest = requests.poll()) != null) {
                if (tracingRequest == PekkoTracingRequest.EMPTY) {
                  continue;
                }
                instrumenter()
                    .end(
                        tracingRequest.context,
                        tracingRequest.request,
                        PekkoHttpServerSingletons.errorResponse(),
                        null);
              }
              completeStage();
            }
          });
    }
  }
}
