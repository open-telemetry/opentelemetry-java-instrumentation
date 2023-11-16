/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27;

import application.io.opentelemetry.api.OpenTelemetry;
import application.io.opentelemetry.api.logs.LoggerProvider;
import application.io.opentelemetry.api.metrics.MeterProvider;
import application.io.opentelemetry.api.trace.TracerProvider;
import application.io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.propagation.ApplicationContextPropagators;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationMeterFactory;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationMeterProvider;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics.ApplicationMeterFactory115;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLoggerProvider;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_4.trace.ApplicationTracerProvider14;
import java.lang.reflect.InvocationTargetException;

public final class ApplicationOpenTelemetry127 implements OpenTelemetry {

  // Accessed with reflection
  @SuppressWarnings("unused")
  public static final OpenTelemetry INSTANCE = new ApplicationOpenTelemetry127();

  private final TracerProvider applicationTracerProvider;
  private final ContextPropagators applicationContextPropagators;
  private final MeterProvider applicationMeterProvider;
  private final LoggerProvider applicationLoggerProvider;

  @SuppressWarnings("UnnecessarilyFullyQualified")
  private ApplicationOpenTelemetry127() {
    io.opentelemetry.api.OpenTelemetry agentOpenTelemetry =
        io.opentelemetry.api.GlobalOpenTelemetry.get();
    applicationTracerProvider =
        new ApplicationTracerProvider14(agentOpenTelemetry.getTracerProvider());
    applicationContextPropagators =
        new ApplicationContextPropagators(agentOpenTelemetry.getPropagators());
    applicationMeterProvider =
        new ApplicationMeterProvider(
            getMeterFactory(), agentOpenTelemetry.getMeterProvider());
    applicationLoggerProvider = new ApplicationLoggerProvider(agentOpenTelemetry.getLogsBridge());
  }

  @Override
  public TracerProvider getTracerProvider() {
    return applicationTracerProvider;
  }

  @Override
  public MeterProvider getMeterProvider() {
    return applicationMeterProvider;
  }

  @Override
  public LoggerProvider getLogsBridge() {
    return applicationLoggerProvider;
  }

  @Override
  public ContextPropagators getPropagators() {
    return applicationContextPropagators;
  }

  private static ApplicationMeterFactory getMeterFactory() {
    try {
      // this class is defined in opentelemetry-api-1.31
      Class<?> clazz =
          Class.forName(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.metrics.ApplicationMeterFactory131");
      return (ApplicationMeterFactory) clazz.getConstructor().newInstance();
    } catch (ClassNotFoundException
             | NoSuchMethodException
             | InstantiationException
             | IllegalAccessException
             | InvocationTargetException exception) {
      return new ApplicationMeterFactory115();
    }
  }
}
