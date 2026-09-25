/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static java.util.Collections.emptyList;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
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
 * <p>The handler overload instrumented here exposes only a status and an {@code ErrorInfo}, so
 * these spans have partial HTTP attributes and describe only the response. <a
 * href="https://github.com/apache/pekko-http/issues/1245">apache/pekko-http#1245</a> added rejected
 * request context in Pekko HTTP 2.0 for richer version-specific instrumentation.
 */
public class PekkoHttpParsingErrorSingletons {

  /**
   * The {@code ErrorInfo} is the one object that pekko-http carries unchanged from the rejection to
   * the parsing error handler, so being marked is what tells a rejection that happened before the
   * request was delivered apart from the failures that happen after, see {@link #startSpan}.
   */
  private static final VirtualField<ErrorInfo, Boolean> PARSING_ERROR =
      VirtualField.find(ErrorInfo.class, Boolean.class);

  private static final Instrumenter<ErrorInfo, HttpResponse> instrumenter =
      JavaagentHttpServerInstrumenters.create(
          PekkoHttpUtil.instrumentationName(),
          new PekkoHttpParsingErrorAttributesGetter(),
          new NoopTextMapGetter());

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
   */
  @Nullable
  public static Object[] startSpan(ErrorInfo info) {
    if (PARSING_ERROR.get(info) == null) {
      return null;
    }

    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, info)) {
      return null;
    }
    Context context = instrumenter.start(parentContext, info);
    // the array carries the state from the enter advice to the exit advice
    return new Object[] {context, context.makeCurrent(), info};
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
    ErrorInfo info = (ErrorInfo) enter[2];

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

    instrumenter.end(context, info, response, error);

    return response;
  }

  private static class NoopTextMapGetter implements TextMapGetter<ErrorInfo> {

    @Override
    public Iterable<String> keys(ErrorInfo carrier) {
      return emptyList();
    }

    @Nullable
    @Override
    public String get(@Nullable ErrorInfo carrier, String key) {
      return null;
    }
  }

  private PekkoHttpParsingErrorSingletons() {}
}
