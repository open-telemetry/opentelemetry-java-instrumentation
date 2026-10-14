/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.PekkoHttpUtil;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route.PekkoRouteHolder;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;

public class PekkoHttpServerSingletons {

  public static final VirtualField<HttpRequest, InetSocketAddress> HTTP_REQUEST_PEER_ADDRESS =
      VirtualField.find(HttpRequest.class, InetSocketAddress.class);

  private static final Instrumenter<HttpRequest, HttpResponse> instrumenter;

  static {
    instrumenter =
        JavaagentHttpServerInstrumenters.create(
            PekkoHttpUtil.instrumentationName(),
            new PekkoHttpServerAttributesGetter(),
            new PekkoHttpServerHeaders());
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return instrumenter;
  }

  /**
   * Ends the span for the given request. Returns the response with the headers added by the
   * response customizer, pekko responses are immutable so a new response is built when the
   * customizer adds headers.
   */
  static HttpResponse endSpan(PekkoTracingRequest tracingRequest, HttpResponse response) {
    // pekko response is immutable so the customizer just captures the added headers
    PekkoHttpResponseMutator responseMutator = new PekkoHttpResponseMutator();
    HttpServerResponseCustomizerHolder.getCustomizer()
        .customize(tracingRequest.context, response, responseMutator);
    // build a new response with the added headers
    List<HttpHeader> headers = responseMutator.getHeaders();
    if (!headers.isEmpty()) {
      response = (HttpResponse) response.addHeaders(headers);
    }

    PekkoRouteHolder routeHolder =
        response
            .getAttribute(PekkoRouteHolder.ATTRIBUTE_KEY)
            .orElse(tracingRequest.initialRouteHolder);
    HttpServerRoute.update(
        tracingRequest.context, HttpServerRouteSource.CONTROLLER, routeHolder.route());
    instrumenter().end(tracingRequest.context, tracingRequest.request, response, null);

    return response;
  }

  /** Ends the span for a request that failed with the given error. */
  static void endSpanWithError(PekkoTracingRequest tracingRequest, @Nullable Throwable error) {
    instrumenter().end(tracingRequest.context, tracingRequest.request, errorResponse(), error);
  }

  public static HttpResponse errorResponse() {
    return (HttpResponse) HttpResponse.create().withStatus(500);
  }

  private PekkoHttpServerSingletons() {}
}
