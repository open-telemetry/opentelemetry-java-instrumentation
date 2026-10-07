/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import org.springframework.core.env.ConfigurableEnvironment;

final class Log4jAppenderInstaller {

  static void install(OpenTelemetry openTelemetry, ConfigurableEnvironment environment) {
    OpenTelemetryAppender.install(
        openTelemetry, StructuredAttributesConfig.getSelector(environment));
  }

  private Log4jAppenderInstaller() {}
}
