/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.internal.logging;

import static java.util.Collections.emptyList;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class LoggingSpanExporterConfigurer {

  private LoggingSpanExporterConfigurer() {}

  public static void enableLoggingExporter(
      SdkTracerProviderBuilder builder, ConfigProperties config) {
    // don't install another instance if the user has already explicitly requested it.
    if (loggingExporterIsNotAlreadyConfigured(config)) {
      builder.addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()));
    }
  }

  private static boolean loggingExporterIsNotAlreadyConfigured(ConfigProperties config) {
    return !config.getList("otel.traces.exporter", emptyList()).contains("logging");
  }
}
