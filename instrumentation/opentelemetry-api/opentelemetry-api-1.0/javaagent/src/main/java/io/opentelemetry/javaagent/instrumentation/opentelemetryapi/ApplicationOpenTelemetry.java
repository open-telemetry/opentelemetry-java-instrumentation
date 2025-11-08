/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import application.io.opentelemetry.api.OpenTelemetry;
import application.io.opentelemetry.api.trace.TracerProvider;
import application.io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.propagation.ApplicationContextPropagators;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracerProvider;
import javax.annotation.Nullable;

// Our convention for accessing agent package
@SuppressWarnings("UnnecessarilyFullyQualified")
public class ApplicationOpenTelemetry implements OpenTelemetry {

  public static final OpenTelemetry INSTANCE;

  static {
    OpenTelemetry instance = getOpenTelemetry156();
    if (instance == null) {
      instance = getOpenTelemetry127();
    }
    if (instance == null) {
      instance = getOpenTelemetry110();
    }
    if (instance == null) {
      instance = new ApplicationOpenTelemetry();
    }
    INSTANCE = instance;
  }

  private final TracerProvider applicationTracerProvider;

  private final ContextPropagators applicationContextPropagators;

  private ApplicationOpenTelemetry() {
    io.opentelemetry.api.OpenTelemetry agentOpenTelemetry =
        io.opentelemetry.api.GlobalOpenTelemetry.get();
    applicationTracerProvider =
        ApplicationTracerProvider.create(agentOpenTelemetry.getTracerProvider());
    applicationContextPropagators =
        new ApplicationContextPropagators(agentOpenTelemetry.getPropagators());
  }

  @Override
  public TracerProvider getTracerProvider() {
    return applicationTracerProvider;
  }

  @Override
  public ContextPropagators getPropagators() {
    return applicationContextPropagators;
  }

  @Nullable
  private static OpenTelemetry getOpenTelemetry156() {
    try {
      // this class is defined in opentelemetry-api-1.56
      Class<?> clazz =
          Class.forName(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator.ApplicationOpenTelemetry156Incubator");
      return (OpenTelemetry) clazz.getField("INSTANCE").get(null);
    } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException exception) {
      return null;
    }
  }

  @Nullable
  private static OpenTelemetry getOpenTelemetry127() {
    try {
      // this class is defined in opentelemetry-api-1.27
      Class<?> clazz =
          Class.forName(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.ApplicationOpenTelemetry127");
      return (OpenTelemetry) clazz.getField("INSTANCE").get(null);
    } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException exception) {
      return null;
    }
  }

  @Nullable
  private static OpenTelemetry getOpenTelemetry110() {
    try {
      // this class is defined in opentelemetry-api-1.10
      Class<?> clazz =
          Class.forName(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.ApplicationOpenTelemetry110");
      return (OpenTelemetry) clazz.getField("INSTANCE").get(null);
    } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException exception) {
      return null;
    }
  }
}
