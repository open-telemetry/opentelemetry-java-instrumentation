/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server;

import static java.util.Collections.emptyList;

import akka.http.scaladsl.model.ErrorInfo;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.AkkaHttpUtil;
import javax.annotation.Nullable;

public final class AkkaHttpParsingErrorSingletons {

  private static final VirtualField<ErrorInfo, Boolean> PARSING_ERROR =
      VirtualField.find(ErrorInfo.class, Boolean.class);
  private static final ScopedThreadValue<ErrorInfo> currentError = new ScopedThreadValue<>();
  private static final Instrumenter<ErrorInfo, HttpResponse> instrumenter =
      JavaagentHttpServerInstrumenters.create(
          AkkaHttpUtil.instrumentationName(),
          new AkkaHttpParsingErrorAttributesGetter(),
          new NoopTextMapGetter());

  public static void markParsingError(ErrorInfo info) {
    PARSING_ERROR.set(info, Boolean.TRUE);
  }

  public static ScopedThreadValue<ErrorInfo> currentError() {
    return currentError;
  }

  public static boolean consumeParsingError(ErrorInfo info) {
    // Entity-stream failures use the same response path, but already have a server span.
    if (PARSING_ERROR.get(info) != null) {
      PARSING_ERROR.set(info, null);
      return true;
    }
    return false;
  }

  public static HttpResponse traceResponse(HttpResponse response) {
    ErrorInfo info = currentError.get();
    if (info == null) {
      return response;
    }
    currentError.set(null);
    // No parsed headers are available. Do not inherit a preceding request's context.
    Context parentContext = Context.root();
    if (!instrumenter.shouldStart(parentContext, info)) {
      return response;
    }
    Context context = instrumenter.start(parentContext, info);
    try {
      response = AkkaHttpResponseMutator.customizeResponse(context, response);
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
