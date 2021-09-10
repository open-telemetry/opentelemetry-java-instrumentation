/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import application.io.opentelemetry.api.trace.Tracer;
import application.io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ApplicationTracerProvider implements TracerProvider {

  private static final Constructor<?> TRACE_PROVIDER_14 = getApplicationTracerProvider14();

  protected final io.opentelemetry.api.trace.TracerProvider agentTracerProvider;

  protected ApplicationTracerProvider(
      io.opentelemetry.api.trace.TracerProvider agentTracerProvider) {
    this.agentTracerProvider = agentTracerProvider;
  }

  private static Constructor getApplicationTracerProvider14() {
    try {
      // this class is defined in opentelemetry-api-1.4
      Class clazz =
          Class.forName(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_4.trace.ApplicationTracerProvider14");
      return clazz.getDeclaredConstructor(io.opentelemetry.api.trace.TracerProvider.class);
    } catch (ClassNotFoundException | NoSuchMethodException exception) {
      return null;
    }
  }

  public static TracerProvider create(
      io.opentelemetry.api.trace.TracerProvider agentTracerProvider) {
    if (TRACE_PROVIDER_14 != null) {
      try {
        return (TracerProvider) TRACE_PROVIDER_14.newInstance(agentTracerProvider);
      } catch (InstantiationException
          | IllegalAccessException
          | InvocationTargetException exception) {
        throw new IllegalStateException("Failed to create ApplicationTracerProvider", exception);
      }
    }

    return new ApplicationTracerProvider(agentTracerProvider);
  }

  @Override
  public Tracer get(String instrumentationName) {
    return new ApplicationTracer(agentTracerProvider.get(instrumentationName));
  }

  @Override
  public Tracer get(String instrumentationName, String instrumentationVersion) {
    return new ApplicationTracer(
        GlobalOpenTelemetry.getTracerProvider().get(instrumentationName, instrumentationVersion));
  }
}
