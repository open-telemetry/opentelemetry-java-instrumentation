/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.instrumentation.api.internal.HttpConstants._OTHER;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static java.util.Collections.emptyList;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.PekkoHttpUtil;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpResponse;

/**
 * Creates spans for requests that pekko-http rejected while parsing them. These requests never
 * reach the user handler, and never become an {@code HttpRequest}, so the regular server
 * instrumentation in {@link PekkoHttpServerTracer} does not see them.
 */
public class PekkoHttpParsingErrorSingletons {

  private static final Instrumenter<PekkoHttpParsingError, HttpResponse> instrumenter =
      JavaagentHttpServerInstrumenters.create(
          PekkoHttpUtil.instrumentationName(),
          new PekkoHttpParsingErrorAttributesGetter(),
          NoopTextMapGetter.INSTANCE,
          // the request method could not be parsed, so it is reported as unknown
          builder ->
              builder.addAttributesExtractor(
                  AttributesExtractor.constant(HTTP_REQUEST_METHOD, _OTHER)));

  /**
   * Starts the span for a rejected request, returning {@code null} when the instrumentation is
   * suppressed. The span is started before the handler runs so that it covers the work a custom
   * handler does to build the response.
   */
  @Nullable
  public static Context startSpan() {
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, PekkoHttpParsingError.INSTANCE)) {
      return null;
    }
    return instrumenter.start(parentContext, PekkoHttpParsingError.INSTANCE);
  }

  /**
   * Ends the span for a rejected request. Returns the response with the headers added by the
   * response customizer, pekko responses are immutable so a new response is built when the
   * customizer adds headers.
   */
  @Nullable
  public static HttpResponse endSpan(
      Context context, @Nullable HttpResponse response, @Nullable Throwable error) {
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

    instrumenter.end(context, PekkoHttpParsingError.INSTANCE, response, error);

    return response;
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
