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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class AwsLambdaFunctionInstrumenter {

  private static final String AWS_TRACE_HEADER_PROP = "com.amazonaws.xray.traceHeader";
  private static final String GET_XRAY_TRACE_ID_METHOD = "getXrayTraceId";
  private static final MapGetter mapGetter = new MapGetter();

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
    com.amazonaws.services.lambda.runtime.Context awsContext = input.getAwsContext();
    Map<String, String> customContext = null;
    if (awsContext != null && awsContext.getClientContext() != null) {
      customContext = awsContext.getClientContext().getCustom();
    }
    String xrayTraceId = getXrayTraceId(awsContext);
    if (customContext != null || !isEmptyOrNull(xrayTraceId)) {
      headers = new HashMap<>(headers);
      if (customContext != null) {
        headers.putAll(customContext);
      }
      if (!isEmptyOrNull(xrayTraceId)) {
        headers.put(AWS_TRACE_HEADER_PROP, xrayTraceId);
      }
    }

    return openTelemetry
        .getPropagators()
        .getTextMapPropagator()
        .extract(Context.root(), headers, mapGetter);
  }

  @Nullable
  private static String getXrayTraceId(
      @Nullable com.amazonaws.services.lambda.runtime.Context awsContext) {
    if (awsContext == null) {
      return null;
    }
    try {
      Method getXrayTraceId = awsContext.getClass().getMethod(GET_XRAY_TRACE_ID_METHOD);
      getXrayTraceId.setAccessible(true);
      Object traceId = getXrayTraceId.invoke(awsContext);
      return traceId instanceof String ? (String) traceId : null;
    } catch (NoSuchMethodException ignored) {
      return null;
    } catch (IllegalAccessException | InvocationTargetException | SecurityException ignored) {
      return null;
    }
  }

  private static boolean isEmptyOrNull(@Nullable String value) {
    return value == null || value.isEmpty();
  }

  private static class MapGetter implements TextMapGetter<Map<String, String>> {

    @Override
    public Iterable<String> keys(Map<String, String> map) {
      return map.keySet();
    }

    @Override
    @Nullable
    public String get(@Nullable Map<String, String> map, String s) {
      if (map == null) {
        return null;
      }
      String value = map.get(s);
      return value != null ? value : map.get(s.toLowerCase(Locale.ROOT));
    }
  }
}
