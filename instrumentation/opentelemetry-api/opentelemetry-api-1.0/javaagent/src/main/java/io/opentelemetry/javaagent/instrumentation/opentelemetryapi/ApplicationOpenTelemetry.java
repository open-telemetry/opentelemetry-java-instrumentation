/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.propagation.ApplicationContextPropagators;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracerProvider;
import javax.annotation.Nullable;

public class ApplicationOpenTelemetry implements application.io.opentelemetry.api.OpenTelemetry {

  public static final application.io.opentelemetry.api.OpenTelemetry INSTANCE;

  static {
    application.io.opentelemetry.api.OpenTelemetry instance = getOpenTelemetry156();
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

  private final application.io.opentelemetry.api.trace.TracerProvider applicationTracerProvider;

  private final application.io.opentelemetry.context.propagation.ContextPropagators
      applicationContextPropagators;

  private ApplicationOpenTelemetry() {
    OpenTelemetry agentOpenTelemetry = GlobalOpenTelemetry.get();
    applicationTracerProvider =
        ApplicationTracerProvider.create(agentOpenTelemetry.getTracerProvider());
    applicationContextPropagators =
        new ApplicationContextPropagators(agentOpenTelemetry.getPropagators());
  }

  @Override
  public application.io.opentelemetry.api.trace.TracerProvider getTracerProvider() {
    return applicationTracerProvider;
  }

  @Override
  public application.io.opentelemetry.context.propagation.ContextPropagators getPropagators() {
    return applicationContextPropagators;
  }

  @Nullable
  private static application.io.opentelemetry.api.OpenTelemetry getOpenTelemetry156() {
    return getOpenTelemetry(
        "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator.ApplicationOpenTelemetry156Incubator");
  }

  @Nullable
  private static application.io.opentelemetry.api.OpenTelemetry getOpenTelemetry127() {
    return getOpenTelemetry(
        "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.ApplicationOpenTelemetry127");
  }

  @Nullable
  private static application.io.opentelemetry.api.OpenTelemetry getOpenTelemetry110() {
    return getOpenTelemetry(
        "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.ApplicationOpenTelemetry110");
  }

  private static application.io.opentelemetry.api.OpenTelemetry getOpenTelemetry(String className) {
    try {
      Class<?> clazz = Class.forName(className);
      return (application.io.opentelemetry.api.OpenTelemetry) clazz.getField("INSTANCE").get(null);
    } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException exception) {
      return null;
    }
  }
}
