/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import application.io.opentelemetry.api.trace.Tracer;
import application.io.opentelemetry.api.trace.TracerProvider;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class ApplicationTracerProvider implements TracerProvider {

  private static final MethodHandle TRACE_PROVIDER_14 = getApplicationTracerProvider14();

  protected final ApplicationTracerFactory tracerFactory;
  protected final io.opentelemetry.api.trace.TracerProvider agentTracerProvider;

  protected ApplicationTracerProvider(
      ApplicationTracerFactory tracerFactory,
      io.opentelemetry.api.trace.TracerProvider agentTracerProvider) {
    this.tracerFactory = tracerFactory;
    this.agentTracerProvider = agentTracerProvider;
  }

  private static MethodHandle getApplicationTracerProvider14() {
    try {
      // this class is defined in opentelemetry-api-1.4
      Class<?> clazz =
          Class.forName(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_4.trace.ApplicationTracerProvider14");
      return MethodHandles.lookup()
          .findConstructor(
              clazz,
              MethodType.methodType(void.class, io.opentelemetry.api.trace.TracerProvider.class));
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
      return null;
    }
  }

  public static TracerProvider create(
      io.opentelemetry.api.trace.TracerProvider agentTracerProvider) {
    if (TRACE_PROVIDER_14 != null) {
      try {
        return (TracerProvider) TRACE_PROVIDER_14.invoke(agentTracerProvider);
      } catch (Throwable throwable) {
        throw new IllegalStateException("Failed to create ApplicationTracerProvider", throwable);
      }
    }

    return new ApplicationTracerProvider(ApplicationTracer::new, agentTracerProvider);
  }

  @Override
  public Tracer get(String instrumentationName) {
    return tracerFactory.newTracer(agentTracerProvider.get(instrumentationName));
  }

  @Override
  public Tracer get(String instrumentationName, String instrumentationVersion) {
    return tracerFactory.newTracer(
        agentTracerProvider.get(instrumentationName, instrumentationVersion));
  }
}
