/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server;

import static java.util.Collections.emptyList;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.ErrorInfo;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.AkkaHttpUtil;
import java.util.List;
import javax.annotation.Nullable;

public final class AkkaHttpParsingErrorSingletons {

  private static final VirtualField<ErrorInfo, Boolean> PARSING_ERROR =
      VirtualField.find(ErrorInfo.class, Boolean.class);
  private static final ThreadLocal<ErrorInfo> currentError = new ThreadLocal<>();
  private static final Instrumenter<ErrorInfo, HttpResponse> instrumenter =
      JavaagentHttpServerInstrumenters.create(
          AkkaHttpUtil.instrumentationName(),
          new AkkaHttpParsingErrorAttributesGetter(),
          new NoopTextMapGetter());

  public static void markParsingError(ErrorInfo info) {
    PARSING_ERROR.set(info, Boolean.TRUE);
  }

  @Nullable
  public static ErrorInfo enter(ErrorInfo info) {
    ErrorInfo previous = currentError.get();
    currentError.remove();
    // Entity-stream failures use the same response path, but already have a server span.
    if (PARSING_ERROR.get(info) != null) {
      PARSING_ERROR.set(info, null);
      currentError.set(info);
    }
    return previous;
  }

  public static void exit(@Nullable ErrorInfo previous) {
    if (previous == null) {
      currentError.remove();
    } else {
      currentError.set(previous);
    }
  }

  public static HttpResponse traceResponse(HttpResponse response) {
    ErrorInfo info = currentError.get();
    if (info == null) {
      return response;
    }
    currentError.remove();
    // No parsed headers are available. Do not inherit a preceding request's context.
    Context parentContext = Context.root();
    if (!instrumenter.shouldStart(parentContext, info)) {
      return response;
    }
    Context context = instrumenter.start(parentContext, info);
    try {
      AkkaHttpResponseMutator responseMutator = new AkkaHttpResponseMutator();
      HttpServerResponseCustomizerHolder.getCustomizer()
          .customize(context, response, responseMutator);
      List<HttpHeader> headers = responseMutator.getHeaders();
      if (!headers.isEmpty()) {
        response = (HttpResponse) response.addHeaders(headers);
      }
      return response;
    } finally {
      instrumenter.end(context, info, response, null);
    }
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

  private AkkaHttpParsingErrorSingletons() {}
}
