/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server;

import akka.http.javadsl.model.HttpResponse;
import akka.http.scaladsl.model.ErrorInfo;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.AkkaHttpUtil;
import javax.annotation.Nullable;

public final class AkkaHttpParsingErrorSingletons {

  private static final VirtualField<ErrorInfo, Boolean> PARSING_ERROR =
      VirtualField.find(ErrorInfo.class, Boolean.class);
  private static final Instrumenter<ErrorInfo, HttpResponse> instrumenter =
      JavaagentHttpServerInstrumenters.create(
          AkkaHttpUtil.instrumentationName(),
          new AkkaHttpParsingErrorAttributesGetter(),
          (carrier, key) -> null);

  public static void markParsingError(ErrorInfo info) {
    PARSING_ERROR.set(info, Boolean.TRUE);
  }

  @Nullable
  public static AdviceScope startSpan(ErrorInfo info) {
    if (PARSING_ERROR.get(info) == null) {
      return null;
    }
    PARSING_ERROR.set(info, null);
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, info)) {
      return null;
    }
    Context context = instrumenter.start(parentContext, info);
    return new AdviceScope(context, context.makeCurrent(), info);
  }

  public static class AdviceScope {
    private final Context context;
    private final Scope scope;
    private final ErrorInfo info;

    private AdviceScope(Context context, Scope scope, ErrorInfo info) {
      this.context = context;
      this.scope = scope;
      this.info = info;
    }

    @Nullable
    public Object end(@Nullable Object response, @Nullable Throwable error) {
      scope.close();
      HttpResponse httpResponse = (HttpResponse) response;
      if (httpResponse != null) {
        httpResponse = AkkaHttpResponseMutator.customizeResponse(context, httpResponse);
      }
      instrumenter.end(context, info, httpResponse, error);
      return httpResponse;
    }
  }

  private AkkaHttpParsingErrorSingletons() {}
}
