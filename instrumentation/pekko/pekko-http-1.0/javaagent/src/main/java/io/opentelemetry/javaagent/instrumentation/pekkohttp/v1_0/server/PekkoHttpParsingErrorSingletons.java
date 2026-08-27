/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.instrumentation.api.internal.HttpConstants._OTHER;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static java.util.Collections.emptyList;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.PekkoHttpUtil;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pekko.http.impl.engine.parsing.HttpMessageParser;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;
import org.apache.pekko.http.scaladsl.model.HttpMethod;
import org.apache.pekko.http.scaladsl.model.Uri;
import org.apache.pekko.util.ByteString;

/**
 * Creates spans for requests that pekko-http rejected while parsing them. These requests never
 * reach the user handler, and never become an {@code HttpRequest}, so the regular server
 * instrumentation in {@link PekkoHttpServerTracer} does not see them.
 */
public class PekkoHttpParsingErrorSingletons {

  /** Holds what the parser read for the request it is currently working on. */
  @SuppressWarnings("rawtypes")
  private static final VirtualField<HttpMessageParser, PekkoHttpParsingError> parserRequestLine =
      VirtualField.find(HttpMessageParser.class, PekkoHttpParsingError.class);

  /**
   * The {@code ErrorInfo} is the one object that pekko-http carries unchanged from the parser to
   * the parsing error handler, so it is what the recovered request line is bound to.
   */
  private static final VirtualField<ErrorInfo, PekkoHttpParsingError> requestLine =
      VirtualField.find(ErrorInfo.class, PekkoHttpParsingError.class);

  private static final Instrumenter<PekkoHttpParsingError, HttpResponse> instrumenter =
      JavaagentHttpServerInstrumenters.create(
          PekkoHttpUtil.instrumentationName(),
          new PekkoHttpParsingErrorAttributesGetter(),
          NoopTextMapGetter.INSTANCE,
          builder -> builder.addAttributesExtractor(UnknownMethodExtractor.INSTANCE));

  /** Discards what an earlier request on the same connection left behind. */
  public static void startRequest(HttpMessageParser<?> parser) {
    parserRequestLine.set(parser, null);
  }

  /** Records a request line that pekko-http parsed and validated. */
  public static void captureParsedRequestLine(
      HttpMessageParser<?> parser, @Nullable HttpMethod method, @Nullable Uri uri) {
    if (uri != null) {
      parserRequestLine.set(parser, PekkoHttpParsingError.parsed(method, uri));
    }
  }

  /** Records a request line whose target is what failed to parse. */
  public static void captureUnparsedRequestLine(
      HttpMessageParser<?> parser, @Nullable HttpMethod method, @Nullable ByteString uriBytes) {
    parserRequestLine.set(parser, PekkoHttpParsingError.unparsed(method, uriBytes));
  }

  /**
   * Moves the request line onto the {@code ErrorInfo}, which is what reaches the parsing error
   * handler. Nothing is recorded when the failure happened before the request line was read, in
   * which case the span is emitted without a method or a target.
   */
  public static void bindRequestLine(HttpMessageParser<?> parser, ErrorInfo info) {
    PekkoHttpParsingError request = parserRequestLine.get(parser);
    if (request != null) {
      requestLine.set(info, request);
    }
  }

  /**
   * Starts the span for a rejected request, returning {@code null} when the instrumentation is
   * suppressed. The span is started before the handler runs so that it covers the work a custom
   * handler does to build the response.
   */
  @Nullable
  public static Object[] startSpan(ErrorInfo info) {
    PekkoHttpParsingError request = requestLine.get(info);
    if (request == null) {
      request = PekkoHttpParsingError.UNKNOWN;
    }

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
    PekkoHttpParsingError request = (PekkoHttpParsingError) enter[2];

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

  /**
   * Reports an unparsed method as unknown. The common http extractor drops the attribute when the
   * getter returns null, and semconv requires it to be present.
   */
  private enum UnknownMethodExtractor
      implements AttributesExtractor<PekkoHttpParsingError, HttpResponse> {
    INSTANCE;

    @Override
    public void onStart(
        AttributesBuilder attributes, Context parentContext, PekkoHttpParsingError request) {
      if (request.method() == null) {
        attributes.put(HTTP_REQUEST_METHOD, _OTHER);
      }
    }

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        PekkoHttpParsingError request,
        @Nullable HttpResponse response,
        @Nullable Throwable error) {}
  }

  private enum NoopTextMapGetter implements TextMapGetter<PekkoHttpParsingError> {
    INSTANCE;

    @Override
    public Iterable<String> keys(PekkoHttpParsingError carrier) {
      return emptyList();
    }

    @Nullable
    @Override
    public String get(@Nullable PekkoHttpParsingError carrier, String key) {
      return null;
    }
  }

  private PekkoHttpParsingErrorSingletons() {}
}
