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
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.PekkoHttpUtil;
import javax.annotation.Nullable;
import org.apache.pekko.http.javadsl.model.HttpResponse;

/**
 * Creates spans for requests that pekko-http rejected while parsing them. These requests never
 * reach the user handler, and never become an {@code HttpRequest}, so the regular server
 * instrumentation in {@link PekkoHttpServerTracer} does not see them.
 */
public final class PekkoHttpParsingErrorSingletons {

  private static final Instrumenter<PekkoHttpParsingError, HttpResponse> INSTRUMENTER =
      JavaagentHttpServerInstrumenters.create(
          PekkoHttpUtil.instrumentationName(),
          new PekkoHttpParsingErrorAttributesGetter(),
          NoopTextMapGetter.INSTANCE,
          // the request method could not be parsed, so it is reported as unknown
          builder ->
              builder.addAttributesExtractor(
                  AttributesExtractor.constant(HTTP_REQUEST_METHOD, _OTHER)));

  public static void emitSpan(HttpResponse response) {
    Context parentContext = Context.current();
    if (!INSTRUMENTER.shouldStart(parentContext, PekkoHttpParsingError.INSTANCE)) {
      return;
    }
    Context context = INSTRUMENTER.start(parentContext, PekkoHttpParsingError.INSTANCE);
    INSTRUMENTER.end(context, PekkoHttpParsingError.INSTANCE, response, null);
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
