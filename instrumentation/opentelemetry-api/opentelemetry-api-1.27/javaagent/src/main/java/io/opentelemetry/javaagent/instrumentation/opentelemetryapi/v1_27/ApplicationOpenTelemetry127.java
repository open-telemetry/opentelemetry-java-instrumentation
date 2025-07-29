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
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracerFactory;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationMeterFactory;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationMeterProvider;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics.ApplicationMeterFactory115;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLoggerFactory;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLoggerFactory127;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLoggerProvider;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.trace.ApplicationTracerFactory127;
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
        new ApplicationTracerProvider14(getTracerFactory(), agentOpenTelemetry.getTracerProvider());
    applicationContextPropagators =
        new ApplicationContextPropagators(agentOpenTelemetry.getPropagators());
    applicationMeterProvider =
        new ApplicationMeterProvider(getMeterFactory(), agentOpenTelemetry.getMeterProvider());
    applicationLoggerProvider =
        new ApplicationLoggerProvider(getLoggerFactory(), agentOpenTelemetry.getLogsBridge());
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
    // this class is defined in opentelemetry-api-1.40
    ApplicationMeterFactory meterFactory =
        getMeterFactory(
            "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics.ApplicationMeterFactory140Incubator");
    if (meterFactory == null) {
      // this class is defined in opentelemetry-api-1.38
      meterFactory =
          getMeterFactory(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.incubator.metrics.ApplicationMeterFactory138Incubator");
    }
    if (meterFactory == null) {
      // this class is defined in opentelemetry-api-1.38
      meterFactory =
          getMeterFactory(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics.ApplicationMeterFactory138");
    }
    if (meterFactory == null) {
      // this class is defined in opentelemetry-api-1.37
      meterFactory =
          getMeterFactory(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.ApplicationMeterFactory137");
    }
    if (meterFactory == null) {
      // this class is defined in opentelemetry-api-1.32
      meterFactory =
          getMeterFactory(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_32.incubator.metrics.ApplicationMeterFactory132Incubator");
    }
    if (meterFactory == null) {
      // this class is defined in opentelemetry-api-1.32
      meterFactory =
          getMeterFactory(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_32.metrics.ApplicationMeterFactory132");
    }
    if (meterFactory == null) {
      // this class is defined in opentelemetry-api-1.31
      meterFactory =
          getMeterFactory(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics.ApplicationMeterFactory131");
    }
    if (meterFactory == null) {
      meterFactory = new ApplicationMeterFactory115();
    }

    return meterFactory;
  }

  private static ApplicationMeterFactory getMeterFactory(String className) {
    return getFactory(className, ApplicationMeterFactory.class);
  }

  private static ApplicationLoggerFactory getLoggerFactory() {
    // this class is defined in opentelemetry-api-1.52
    ApplicationLoggerFactory loggerFactory =
        getLoggerFactory(
            "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_52.incubator.logs.ApplicationLoggerFactory150Incubator");
    // this class is defined in opentelemetry-api-1.50
    if (loggerFactory == null) {
      loggerFactory =
          getLoggerFactory(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.incubator.logs.ApplicationLoggerFactory150Incubator");
    }
    if (loggerFactory == null) {
      // this class is defined in opentelemetry-api-1.50
      loggerFactory =
          getLoggerFactory(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.logs.ApplicationLoggerFactory150");
    }
    if (loggerFactory == null) {
      // this class is defined in opentelemetry-api-1.47
      loggerFactory =
          getLoggerFactory(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_47.incubator.logs.ApplicationLoggerFactory147Incubator");
    }
    if (loggerFactory == null) {
      // this class is defined in opentelemetry-api-1.42
      loggerFactory =
          getLoggerFactory(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_42.incubator.logs.ApplicationLoggerFactory142Incubator");
    }
    if (loggerFactory == null) {
      // this class is defined in opentelemetry-api-1.42
      loggerFactory =
          getLoggerFactory(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_42.logs.ApplicationLoggerFactory142");
    }
    if (loggerFactory == null) {
      // this class is defined in opentelemetry-api-1.40
      loggerFactory =
          getLoggerFactory(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.logs.ApplicationLoggerFactory140Incubator");
    }
    if (loggerFactory == null) {
      loggerFactory = new ApplicationLoggerFactory127();
    }

    return loggerFactory;
  }

  private static ApplicationLoggerFactory getLoggerFactory(String className) {
    return getFactory(className, ApplicationLoggerFactory.class);
  }

  private static ApplicationTracerFactory getTracerFactory() {
    // this class is defined in opentelemetry-api-1.47
    ApplicationTracerFactory tracerFactory =
        getTracerFactory(
            "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_47.incubator.trace.ApplicationTracerFactory147Incubator");
    if (tracerFactory == null) {
      // this class is defined in opentelemetry-api-1.40
      tracerFactory =
          getTracerFactory(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.trace.ApplicationTracerFactory140Incubator");
    }
    if (tracerFactory == null) {
      tracerFactory = new ApplicationTracerFactory127();
    }

    return tracerFactory;
  }

  private static ApplicationTracerFactory getTracerFactory(String className) {
    return getFactory(className, ApplicationTracerFactory.class);
  }

  private static <T> T getFactory(String className, Class<T> factoryClass) {
    try {
      Class<?> clazz = Class.forName(className);
      return factoryClass.cast(clazz.getConstructor().newInstance());
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | InstantiationException
        | IllegalAccessException
        | InvocationTargetException exception) {
      return null;
    }
  }
}
