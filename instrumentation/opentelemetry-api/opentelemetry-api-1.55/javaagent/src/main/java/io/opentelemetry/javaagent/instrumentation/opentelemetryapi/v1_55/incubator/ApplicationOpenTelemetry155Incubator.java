package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_55.incubator;

import application.io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import application.io.opentelemetry.api.incubator.config.ConfigProvider;

import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.ApplicationOpenTelemetry127;

public class ApplicationOpenTelemetry155Incubator extends ApplicationOpenTelemetry127
    implements ExtendedOpenTelemetry {

  io.opentelemetry.api.incubator.ExtendedOpenTelemetry extendedOpenTelemetry =
      (io.opentelemetry.api.incubator.ExtendedOpenTelemetry)
          io.opentelemetry.api.GlobalOpenTelemetry.get();

  @Override
  public ConfigProvider getConfigProvider() {
    return extendedOpenTelemetry.getConfigProvider();
  }
}
