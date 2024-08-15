/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class AwsLambdaFunctionInstrumenter {

  private final OpenTelemetry openTelemetry;
  final Instrumenter<AwsLambdaRequest, Object> instrumenter;

  public AwsLambdaFunctionInstrumenter(
      OpenTelemetry openTelemetry, Instrumenter<AwsLambdaRequest, Object> instrumenter) {
    this.openTelemetry = openTelemetry;
    this.instrumenter = instrumenter;
  }

  public boolean shouldStart(Context parentContext, AwsLambdaRequest input) {
    return instrumenter.shouldStart(parentContext, input);
  }

  public Context start(Context parentContext, AwsLambdaRequest input) {
    return instrumenter.start(parentContext, input);
  }

  public void end(
      Context context,
      AwsLambdaRequest input,
      @Nullable Object response,
      @Nullable Throwable error) {
    instrumenter.end(context, input, response, error);
  }

  public Context extract(AwsLambdaRequest input) {
    ContextPropagationDebug.debugContextLeakIfEnabled();
    // Look in both the http headers and the custom client context
    Map<String, String> headers = input.getHeaders();
    if (input.getAwsContext() != null && input.getAwsContext().getClientContext() != null) {
      Map<String, String> customContext = input.getAwsContext().getClientContext().getCustom();
      if (customContext != null) {
        headers = new HashMap<>(headers);
        headers.putAll(customContext);
      }
    }

    return openTelemetry
        .getPropagators()
        .getTextMapPropagator()
        .extract(Context.root(), headers, MapGetter.INSTANCE);
  }

  private enum MapGetter implements TextMapGetter<Map<String, String>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, String> map) {
      return map.keySet();
    }

    @Override
    public String get(Map<String, String> map, String s) {
      return map.get(s.toLowerCase(Locale.ROOT));
    }
  }
}
