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
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pekko.http.impl.engine.parsing.HttpMessageParser;
import org.apache.pekko.http.impl.engine.parsing.ParserOutput;
import org.apache.pekko.http.impl.engine.parsing.ParserOutput.MessageStartError;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;
import org.apache.pekko.http.scaladsl.model.HttpMethod;
import org.apache.pekko.http.scaladsl.model.Uri;
import org.apache.pekko.stream.Attributes;
import org.apache.pekko.stream.stage.GraphStageLogic;
import org.apache.pekko.util.ByteString;

/**
 * Creates spans for requests that pekko-http rejected while parsing them. These requests never
 * reach the user handler, and never become an {@code HttpRequest}, so the regular server
 * instrumentation in {@link PekkoHttpServerTracer} does not see them.
 */
public class PekkoHttpParsingErrorSingletons {

  /** Holds what the parser read for the request it is currently working on. */
  @SuppressWarnings("rawtypes")
  private static final VirtualField<HttpMessageParser, PekkoHttpRequestLine> PARSER_REQUEST_LINE =
      VirtualField.find(HttpMessageParser.class, PekkoHttpRequestLine.class);

  /**
   * The {@code ErrorInfo} is the one object that pekko-http carries unchanged from the rejection to
   * the parsing error handler, so it is what the recovered request is bound to. Being bound at all
   * is also what marks a rejection as one that happened before the request was delivered, see
   * {@link #startSpan}.
   */
  private static final VirtualField<ErrorInfo, PekkoHttpParsingError> REQUEST_LINE =
      VirtualField.find(ErrorInfo.class, PekkoHttpParsingError.class);

  private static final Instrumenter<PekkoHttpParsingError, HttpResponse> instrumenter =
      JavaagentHttpServerInstrumenters.create(
          PekkoHttpUtil.instrumentationName(),
          new PekkoHttpParsingErrorAttributesGetter(),
          new NoopTextMapGetter(),
          builder -> builder.addAttributesExtractor(new UnknownMethodExtractor()));

  /** Discards what an earlier request on the same connection left behind. */
  public static void startRequest(HttpMessageParser<?> parser) {
    PARSER_REQUEST_LINE.set(parser, null);
  }

  /** Records a request line that pekko-http parsed and validated. */
  public static void captureParsedRequestLine(
      HttpMessageParser<?> parser, @Nullable HttpMethod method, @Nullable Uri uri) {
    if (uri != null) {
      PARSER_REQUEST_LINE.set(parser, PekkoHttpRequestLine.parsed(method, uri));
    }
  }

  /** Records a request line whose target is what failed to parse. */
  public static void captureUnparsedRequestLine(
      HttpMessageParser<?> parser, @Nullable HttpMethod method, @Nullable ByteString uriBytes) {
    PARSER_REQUEST_LINE.set(parser, PekkoHttpRequestLine.unparsed(method, uriBytes));
  }

  /**
   * Moves what the parser read onto the {@code ErrorInfo}, which is what reaches the parsing error
   * handler. A failure that happened before the request line was read is bound as unknown, so that
   * the binding marks every rejection the parser produced, whether or not there is anything to say
   * about the request.
   *
   * <p>Called for every output of every message parser, the one that reads client responses
   * included, so it does as little as possible for the ones that are not a failure.
   */
  public static void bindRequestLine(HttpMessageParser<?> parser, ParserOutput output) {
    if (!(output instanceof MessageStartError)) {
      return;
    }
    InetSocketAddress peerAddress = peerAddress(parser);
    PekkoHttpRequestLine requestLine = PARSER_REQUEST_LINE.get(parser);
    REQUEST_LINE.set(
        ((MessageStartError) output).info(),
        requestLine == null
            ? PekkoHttpParsingError.unknown(peerAddress)
            : requestLine.toParsingError(peerAddress));
  }

  /**
   * Marks a rejection that reaches the parsing error handler without having passed through a
   * parser. The stage that resolves a request to an absolute uri rejects a CONNECT request and an
   * unusable {@code Host} header this way, and those never reached the user handler either, so they
   * are traced with nothing known about them.
   *
   * <p>What the parser read overwrites this, because a {@code MessageStartError} is built before it
   * is emitted.
   */
  public static void markParsingError(ErrorInfo info) {
    REQUEST_LINE.set(info, PekkoHttpParsingError.unknown(null));
  }

  /**
   * The peer address is not on the parser, it is on the stream attributes of the connection the
   * parser was materialized for, which {@link HttpPrepareAttributesInstrumentation} put it on.
   */
  @Nullable
  private static InetSocketAddress peerAddress(HttpMessageParser<?> parser) {
    if (!(parser instanceof GraphStageLogic)) {
      return null;
    }
    Attributes attributes = ((GraphStageLogic) parser).attributes();
    if (attributes == null) {
      return null;
    }
    return attributes
        .getAttribute(PekkoHttpServerRemoteAddress.class)
        .map(PekkoHttpServerRemoteAddress::getAddress)
        .orElse(null);
  }

  /**
   * Starts the span for a rejected request, returning {@code null} when there is no span to start.
   * The span is started before the handler runs so that it covers the work a custom handler does to
   * build the response.
   *
   * <p>An {@code ErrorInfo} that nothing was bound to is not a rejected request. The same handler
   * answers a request entity stream that failed and a response stream that failed, and both of
   * those happen after the request was delivered and already has a span of its own.
   */
  @Nullable
  public static Object[] startSpan(ErrorInfo info) {
    PekkoHttpParsingError request = REQUEST_LINE.get(info);
    if (request == null) {
      return null;
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
  private static class UnknownMethodExtractor
      implements AttributesExtractor<PekkoHttpParsingError, HttpResponse> {

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

  private static class NoopTextMapGetter implements TextMapGetter<PekkoHttpParsingError> {

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
