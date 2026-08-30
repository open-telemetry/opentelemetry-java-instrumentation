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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
  private static final ClassValue<MethodHandleHolder> GET_XRAY_TRACE_ID =
      new ClassValue<MethodHandleHolder>() {
        @Override
        protected MethodHandleHolder computeValue(Class<?> type) {
          return new MethodHandleHolder(findGetXrayTraceId(type));
        }
      };
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
    MethodHandle getXrayTraceId = GET_XRAY_TRACE_ID.get(awsContext.getClass()).methodHandle;
    if (getXrayTraceId == null) {
      return null;
    }
    try {
      Object traceId = getXrayTraceId.invoke(awsContext);
      return traceId instanceof String ? (String) traceId : null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  @Nullable
  private static MethodHandle findGetXrayTraceId(Class<?> type) {
    MethodHandle methodHandle = findPublicGetXrayTraceId(type);
    if (methodHandle != null) {
      return methodHandle;
    }
    for (Class<?> interfaceType : type.getInterfaces()) {
      methodHandle = findGetXrayTraceId(interfaceType);
      if (methodHandle != null) {
        return methodHandle;
      }
    }
    Class<?> superClass = type.getSuperclass();
    return superClass == null ? null : findGetXrayTraceId(superClass);
  }

  @Nullable
  private static MethodHandle findPublicGetXrayTraceId(Class<?> type) {
    try {
      return MethodHandles.publicLookup()
          .findVirtual(type, GET_XRAY_TRACE_ID_METHOD, MethodType.methodType(String.class));
    } catch (NoSuchMethodException | IllegalAccessException | SecurityException e) {
      return null;
    }
  }

  private static boolean isEmptyOrNull(@Nullable String value) {
    return value == null || value.isEmpty();
  }

  private static class MethodHandleHolder {
    @Nullable final MethodHandle methodHandle;

    private MethodHandleHolder(@Nullable MethodHandle methodHandle) {
      this.methodHandle = methodHandle;
    }
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
