/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.PekkoHttpUtil;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;

/**
 * Creates spans for requests that pekko-http rejected while parsing them. These requests never
 * reach the user handler, and never become an {@code HttpRequest}, so the regular server
 * instrumentation does not see them.
 *
 * <p>The handler overload that every Pekko HTTP version calls exposes only a status and an {@code
 * ErrorInfo}, so on Pekko HTTP 1.x these spans have partial HTTP attributes and describe only the
 * response. <a href="https://github.com/apache/pekko-http/issues/1245">apache/pekko-http#1245</a>
 * added an overload in Pekko HTTP 2.0 that also carries what was read of the rejected request, and
 * a span started from that one reports the method, target and protocol as well.
 */
public class PekkoHttpParsingErrorSingletons {

  /**
   * The {@code ErrorInfo} is the one object that pekko-http carries unchanged from the rejection to
   * the parsing error handler, so being marked is what tells a rejection that happened before the
   * request was delivered apart from the failures that happen after, see {@link #startSpan}.
   */
  private static final VirtualField<ErrorInfo, Boolean> PARSING_ERROR =
      VirtualField.find(ErrorInfo.class, Boolean.class);

  private static final Instrumenter<PekkoHttpParsingErrorRequest, HttpResponse> instrumenter =
      JavaagentHttpServerInstrumenters.create(
          PekkoHttpUtil.instrumentationName(),
          new PekkoHttpParsingErrorAttributesGetter(),
          (carrier, key) -> null);

  /** Marks a rejection that was made before the request was delivered. */
  public static void markParsingError(ErrorInfo info) {
    PARSING_ERROR.set(info, Boolean.TRUE);
  }

  /**
   * Starts the span for a rejected request, returning {@code null} when there is no span to start.
   * The span is started before the handler runs so that it covers the work a custom handler does to
   * build the response.
   *
   * <p>An {@code ErrorInfo} that was not marked is not a rejected request. The same handler answers
   * a request entity stream that failed and a response stream that failed, and both of those happen
   * after the request was delivered and already has a span of its own.
   *
   * <p>The mark is consumed, so a custom handler that delegates to another handler, for example to
   * {@code DefaultParsingErrorHandler}, produces one span rather than one per handler. Span
   * suppression does not cover that when it is configured with the {@code none} strategy. In Pekko
   * HTTP 2.0 the handler overload that carries the rejected request delegates to the one that does
   * not by default, and both are instrumented, which the consumed mark covers as well.
   */
  @Nullable
  public static Object[] startSpan(ErrorInfo info) {
    return startSpan(info, null, null, null);
  }

  /**
   * Starts the span for a rejected request of which the method, request-target and protocol may be
   * known, see {@link #startSpan(ErrorInfo)}.
   */
  @Nullable
  public static Object[] startSpan(
      ErrorInfo info,
      @Nullable String method,
      @Nullable String rawRequestTarget,
      @Nullable String protocol) {
    if (PARSING_ERROR.get(info) == null) {
      return null;
    }
    PARSING_ERROR.set(info, null);

    PekkoHttpParsingErrorRequest request =
        new PekkoHttpParsingErrorRequest(info, method, rawRequestTarget, protocol);
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return null;
    }
    Context context = instrumenter.start(parentContext, request);
    // the array carries the state from the enter advice to the exit advice
    return new Object[] {context, context.makeCurrent(), request};
  }

  /**
   * Ends the span for a rejected request. Returns the response with the headers added by the
   * response customizer, pekko responses are immutable so a new response is built when the
   * customizer adds headers.
   */
  @Nullable
  public static HttpResponse endSpan(
      Object[] enter, @Nullable HttpResponse response, @Nullable Throwable error) {
    ((Scope) enter[1]).close();
    Context context = (Context) enter[0];
    PekkoHttpParsingErrorRequest request = (PekkoHttpParsingErrorRequest) enter[2];

    if (response != null) {
      // pekko response is immutable so the customizer just captures the added headers
      PekkoHttpResponseMutator responseMutator = new PekkoHttpResponseMutator();
      HttpServerResponseCustomizerHolder.getCustomizer()
          .customize(context, response, responseMutator);
      // build a new response with the added headers
      List<HttpHeader> headers = responseMutator.getHeaders();
      if (!headers.isEmpty()) {
        response = response.addHeaders(headers);
      }
    }

    instrumenter.end(context, request, response, error);

    return response;
  }

  private PekkoHttpParsingErrorSingletons() {}
}
